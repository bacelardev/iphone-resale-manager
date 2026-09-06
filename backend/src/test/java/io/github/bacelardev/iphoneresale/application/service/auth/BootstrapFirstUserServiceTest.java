package io.github.bacelardev.iphoneresale.application.service.auth;

import io.github.bacelardev.iphoneresale.application.dto.auth.CreateBootstrapUser;
import io.github.bacelardev.iphoneresale.application.port.security.AuthUserStore;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import io.github.bacelardev.iphoneresale.config.properties.BootstrapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapFirstUserServiceTest {

    @Mock
    private AuthUserStore userStore;

    @Mock
    private PasswordHashService passwordHashService;

    @Test
    void doesNothingWhenDisabled() {
        BootstrapProperties properties = properties(false, "", "", "");

        boolean created = service(properties).bootstrapIfRequired();

        assertThat(created).isFalse();
        verifyNoInteractions(userStore, passwordHashService);
    }

    @Test
    void failsBeforeDatabaseAccessWhenEnabledConfigurationIsIncomplete() {
        BootstrapProperties properties = properties(
                true, "Sócio", String.join("", "so", "cio"), "");

        assertThatThrownBy(() -> service(properties).bootstrapIfRequired())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_PASSWORD");
        verifyNoInteractions(userStore, passwordHashService);
    }

    @Test
    void neverResetsAnExistingUser() {
        String bootstrapPassword = UUID.randomUUID().toString();
        BootstrapProperties properties = properties(
                true, "Sócio", String.join("", "so", "cio"), bootstrapPassword);
        when(userStore.countUsers()).thenReturn(1L);

        boolean created = service(properties).bootstrapIfRequired();

        assertThat(created).isFalse();
        verify(userStore).acquireBootstrapLock();
        verify(passwordHashService, never()).encode(bootstrapPassword);
        verify(userStore, never()).createBootstrapUser(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsExactlyOneNormalizedSocioWhenDatabaseIsEmpty() {
        String bootstrapUsername = String.join(".", "SOCIO", "INICIAL");
        String bootstrapPassword = UUID.randomUUID().toString();
        BootstrapProperties properties = properties(
                true, "  Sócio Inicial  ", bootstrapUsername, bootstrapPassword);
        when(userStore.countUsers()).thenReturn(0L);
        when(passwordHashService.encode(bootstrapPassword)).thenReturn("bcrypt-hash");

        boolean created = service(properties).bootstrapIfRequired();

        assertThat(created).isTrue();
        ArgumentCaptor<CreateBootstrapUser> captor =
                ArgumentCaptor.forClass(CreateBootstrapUser.class);
        verify(userStore).createBootstrapUser(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateBootstrapUser(
                "Sócio Inicial", bootstrapUsername.toLowerCase(Locale.ROOT), "bcrypt-hash"));
    }

    private BootstrapFirstUserService service(BootstrapProperties properties) {
        return new BootstrapFirstUserService(properties, userStore, passwordHashService);
    }

    private static BootstrapProperties properties(
            boolean enabled,
            String name,
            String username,
            String password
    ) {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setEnabled(enabled);
        properties.setName(name);
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }
}
