package io.github.bacelardev.iphoneresale.application.service.user;

import io.github.bacelardev.iphoneresale.application.port.security.CurrentUserIdProvider;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.application.service.auth.SessionRevocationService;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AppUserJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.auth.UserResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.user.CreateUserRequest;
import io.github.bacelardev.iphoneresale.web.dto.user.UpdateUserRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,50}$");
    private static final Map<String, String> SORT = Map.of(
            "name", "name", "username", "username", "createdAt", "createdAt", "updatedAt", "updatedAt"
    );

    private final AppUserJpaRepository users;
    private final PasswordHashService passwords;
    private final CurrentUserIdProvider currentUser;
    private final SessionRevocationService sessions;
    private final AuditService audit;

    public UserService(
            AppUserJpaRepository users,
            PasswordHashService passwords,
            CurrentUserIdProvider currentUser,
            SessionRevocationService sessions,
            AuditService audit
    ) {
        this.users = users;
        this.passwords = passwords;
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String name = cleanName(request.name());
        String username = normalizeUsername(request.username());
        ensureUsernameAvailable(username, null);
        AppUser user = users.saveAndFlush(AppUser.createSocio(name, username, passwords.encode(request.password())));
        audit.record(AuditAction.CREATED, AuditedEntityType.USER, user.getId(), username,
                "Usuário " + name + " criado.", Map.of("name", name, "username", username, "active", true));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(user(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, Boolean active, int page, int size, String sort) {
        Pageable pageable = PageableFactory.create(page, size, sort, SORT, "name", Sort.Direction.ASC);
        return PageResponse.from(users.findAll(filter(search, active), pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        if (request.name() == null && request.username() == null && request.newPassword() == null) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Informe ao menos um campo para atualizar.");
        }
        AppUser user = lockedUser(id);
        VersionGuard.require(user.getVersion(), request.expectedVersion());
        String name = request.name() == null ? user.getName() : cleanName(request.name());
        String username = request.username() == null ? user.getUsername() : normalizeUsername(request.username());
        ensureUsernameAvailable(username, id);
        boolean passwordChanged = request.newPassword() != null;
        if (passwordChanged && !id.equals(currentUserId())) {
            throw BusinessException.unprocessable("PASSWORD_CHANGE_NOT_ALLOWED",
                    "A senha só pode ser alterada pelo próprio usuário.");
        }
        user.update(name, username, passwordChanged ? passwords.encode(request.newPassword()) : null);
        users.flush();
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("name", name);
        changes.put("username", username);
        if (passwordChanged) changes.put("passwordChanged", true);
        audit.record(AuditAction.UPDATED, AuditedEntityType.USER, id, username,
                "Usuário " + name + " atualizado.", changes);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setActive(UUID id, long expectedVersion, boolean active) {
        AppUser user;
        if (active) {
            user = lockedUser(id);
        } else {
            List<AppUser> activeUsers = users.findAllActiveForUpdate();
            user = activeUsers.stream().filter(candidate -> candidate.getId().equals(id)).findFirst()
                    .orElseGet(() -> users.findById(id).orElseThrow(UserService::notFound));
        }
        VersionGuard.require(user.getVersion(), expectedVersion);
        if (user.isActive() == active) return UserResponse.from(user);
        if (!active && id.equals(currentUserId())) {
            throw BusinessException.unprocessable("CANNOT_DEACTIVATE_CURRENT_USER",
                    "O usuário atual não pode desativar a própria conta.");
        }
        if (!active && users.countByActiveTrue() <= 1) {
            throw BusinessException.unprocessable("LAST_ACTIVE_USER_REQUIRED",
                    "Ao menos um sócio deve permanecer ativo.");
        }
        boolean changed = active ? user.activate() : user.deactivate();
        if (changed) users.flush();
        if (changed && !active) sessions.revokeAllSessionsForUser(id);
        if (changed) {
            audit.record(active ? AuditAction.ACTIVATED : AuditAction.DEACTIVATED,
                    AuditedEntityType.USER, id, user.getUsername(),
                    "Usuário " + user.getName() + (active ? " ativado." : " desativado."),
                    Map.of("active", active));
        }
        return UserResponse.from(user);
    }

    private AppUser user(UUID id) {
        return users.findById(id).orElseThrow(UserService::notFound);
    }

    private AppUser lockedUser(UUID id) {
        return users.findByIdForUpdate(id).orElseThrow(UserService::notFound);
    }

    private void ensureUsernameAvailable(String username, UUID currentId) {
        boolean exists = currentId == null ? users.findByUsername(username).isPresent()
                : users.existsByUsernameAndIdNot(username, currentId);
        if (exists) {
            throw BusinessException.conflict("USERNAME_ALREADY_EXISTS", "Já existe um usuário com esse username.");
        }
    }

    private UUID currentUserId() {
        return currentUser.currentUserId().orElseThrow(() ->
                BusinessException.badRequest("UNAUTHORIZED", "Usuário autenticado não encontrado."));
    }

    private static String cleanName(String raw) {
        if (!raw.equals(raw.trim()) || raw.isBlank()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "O nome não pode possuir espaços externos.");
        }
        return raw;
    }

    private static String normalizeUsername(String raw) {
        String username = raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Username inválido.");
        }
        return username;
    }

    private static Specification<AppUser> filter(String search, Boolean active) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("username")), like)));
            }
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static BusinessException notFound() {
        return BusinessException.notFound("USER_NOT_FOUND", "Usuário não encontrado.");
    }
}
