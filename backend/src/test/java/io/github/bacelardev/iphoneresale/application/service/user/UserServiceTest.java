package io.github.bacelardev.iphoneresale.application.service.user;

import io.github.bacelardev.iphoneresale.application.port.security.CurrentUserIdProvider;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.auth.SessionRevocationService;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.AppUserJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.user.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AppUserJpaRepository users;
    @Mock private PasswordHashService passwords;
    @Mock private CurrentUserIdProvider currentUser;
    @Mock private SessionRevocationService sessions;
    @Mock private AuditService audit;
    @Mock private AppUser target;

    @Test
    void rejectsSelfDeactivationBeforeChangingStateOrSessions() {
        UUID targetId = UUID.randomUUID();
        activeTarget(targetId, 3L);
        when(users.findAllActiveForUpdate()).thenReturn(List.of(target));
        when(currentUser.currentUserId()).thenReturn(Optional.of(targetId));

        assertCode(() -> service().setActive(targetId, 3L, false),
                "CANNOT_DEACTIVATE_CURRENT_USER");

        verify(target, never()).deactivate();
        verify(users, never()).countByActiveTrue();
        verify(sessions, never()).revokeAllSessionsForUser(targetId);
    }

    @Test
    void rejectsDeactivationWhenItWouldRemoveLastActiveSocio() {
        UUID targetId = UUID.randomUUID();
        activeTarget(targetId, 4L);
        when(users.findAllActiveForUpdate()).thenReturn(List.of(target));
        when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(users.countByActiveTrue()).thenReturn(1L);

        assertCode(() -> service().setActive(targetId, 4L, false),
                "LAST_ACTIVE_USER_REQUIRED");

        verify(target, never()).deactivate();
        verify(sessions, never()).revokeAllSessionsForUser(targetId);
    }

    @Test
    void rejectsChangingAnotherUsersPasswordWithoutHashingIt() {
        UUID targetId = UUID.randomUUID();
        when(users.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(target.getVersion()).thenReturn(0L);
        when(target.getName()).thenReturn("Outro Sócio");
        when(target.getUsername()).thenReturn("outro.socio");
        when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));

        assertCode(() -> service().update(targetId,
                        new UpdateUserRequest(0L, null, null, "new-password-123")),
                "PASSWORD_CHANGE_NOT_ALLOWED");

        verify(passwords, never()).encode("new-password-123");
        verify(target, never()).update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private void activeTarget(UUID id, long version) {
        when(target.getId()).thenReturn(id);
        when(target.getVersion()).thenReturn(version);
        when(target.isActive()).thenReturn(true);
    }

    private UserService service() {
        return new UserService(users, passwords, currentUser, sessions, audit);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
