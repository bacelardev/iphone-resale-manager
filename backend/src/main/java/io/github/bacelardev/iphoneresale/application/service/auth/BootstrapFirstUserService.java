package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.CreateBootstrapUser;
import io.github.bacelardev.iphoneresale.application.port.security.AuthUserStore;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import io.github.bacelardev.iphoneresale.config.properties.BootstrapProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class BootstrapFirstUserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,50}$");

    private final BootstrapProperties properties;
    private final AuthUserStore userStore;
    private final PasswordHashService passwordHashService;

    public BootstrapFirstUserService(
            BootstrapProperties properties,
            AuthUserStore userStore,
            PasswordHashService passwordHashService
    ) {
        this.properties = properties;
        this.userStore = userStore;
        this.passwordHashService = passwordHashService;
    }

    @Transactional
    public boolean bootstrapIfRequired() {
        if (!properties.isEnabled()) {
            return false;
        }

        String name = requiredTrimmed(properties.getName(), "APP_BOOTSTRAP_NAME");
        String username = requiredTrimmed(properties.getUsername(), "APP_BOOTSTRAP_USERNAME")
                .toLowerCase(Locale.ROOT);
        String password = required(properties.getPassword(), "APP_BOOTSTRAP_PASSWORD");

        if (name.length() > 120) {
            throw new IllegalStateException("APP_BOOTSTRAP_NAME must have at most 120 characters");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException("APP_BOOTSTRAP_USERNAME has an invalid format");
        }
        if (password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("APP_BOOTSTRAP_PASSWORD must have between 12 and 128 characters");
        }

        userStore.acquireBootstrapLock();
        if (userStore.countUsers() > 0) {
            return false;
        }

        userStore.createBootstrapUser(new CreateBootstrapUser(
                name,
                username,
                passwordHashService.encode(password)
        ));
        return true;
    }

    private static String requiredTrimmed(String value, String environmentName) {
        return required(value, environmentName).trim();
    }

    private static String required(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required when bootstrap is enabled");
        }
        return value;
    }
}
