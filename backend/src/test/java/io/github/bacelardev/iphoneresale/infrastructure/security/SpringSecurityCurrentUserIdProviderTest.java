package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.domain.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityCurrentUserIdProviderTest {

    private final SpringSecurityCurrentUserIdProvider provider =
            new SpringSecurityCurrentUserIdProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAuthenticatedApplicationUserId() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(userId, "socio", UserRole.SOCIO);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, AuthorityUtils.createAuthorityList("ROLE_SOCIO"))
        );

        assertThat(provider.currentUserId()).contains(userId);
    }

    @Test
    void ignoresMissingAnonymousAndForeignPrincipals() {
        assertThat(provider.currentUserId()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(provider.currentUserId()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "foreign", null, AuthorityUtils.createAuthorityList("ROLE_SOCIO"))
        );
        assertThat(provider.currentUserId()).isEmpty();
    }
}
