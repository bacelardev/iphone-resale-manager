package io.github.bacelardev.iphoneresale.application.port.security;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthUserData;
import io.github.bacelardev.iphoneresale.application.dto.auth.CreateBootstrapUser;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserStore {

    Optional<AuthUserData> findByUsername(String normalizedUsername);

    Optional<AuthUserData> findActiveById(UUID id);

    long countUsers();

    void acquireBootstrapLock();

    AuthUserData createBootstrapUser(CreateBootstrapUser command);
}
