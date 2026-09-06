package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.port.security.CurrentUserIdProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SpringSecurityCurrentUserIdProvider implements CurrentUserIdProvider {

    @Override
    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal.userId());
    }
}
