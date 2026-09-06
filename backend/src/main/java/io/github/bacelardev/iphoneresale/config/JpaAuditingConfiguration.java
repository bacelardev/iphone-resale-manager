package io.github.bacelardev.iphoneresale.config;

import io.github.bacelardev.iphoneresale.application.port.security.CurrentUserIdProvider;
import io.github.bacelardev.iphoneresale.domain.model.AppUser;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "currentUserAuditor")
public class JpaAuditingConfiguration {

    @Bean
    AuditorAware<AppUser> currentUserAuditor(
            CurrentUserIdProvider currentUserIdProvider,
            EntityManager entityManager
    ) {
        return () -> currentUserIdProvider.currentUserId()
                .map(userId -> entityManager.getReference(AppUser.class, userId));
    }
}
