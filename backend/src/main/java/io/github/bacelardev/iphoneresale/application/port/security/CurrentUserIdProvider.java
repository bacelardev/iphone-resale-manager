package io.github.bacelardev.iphoneresale.application.port.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the authenticated business user's identifier without coupling the
 * application layer to Spring Security or to a specific identity provider.
 */
@FunctionalInterface
public interface CurrentUserIdProvider {

    Optional<UUID> currentUserId();
}
