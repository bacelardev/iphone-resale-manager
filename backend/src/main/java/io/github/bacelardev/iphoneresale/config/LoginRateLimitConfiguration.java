package io.github.bacelardev.iphoneresale.config;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;
import io.github.bacelardev.iphoneresale.config.properties.AuthProperties;
import io.github.bacelardev.iphoneresale.infrastructure.security.InMemoryLoginRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Two independent login limiters: one keyed by the resolved client address and one keyed by the
 * normalized account identifier, so rotating addresses cannot bypass the per-account limit.
 */
@Configuration(proxyBeanMethods = false)
public class LoginRateLimitConfiguration {

    public static final String CLIENT_LIMITER = "clientLoginAttemptLimiter";
    public static final String ACCOUNT_LIMITER = "accountLoginAttemptLimiter";

    @Bean(CLIENT_LIMITER)
    LoginAttemptLimiter clientLoginAttemptLimiter(AuthProperties properties, Clock clock) {
        return new InMemoryLoginRateLimiter(properties.loginMaxAttempts(), properties.loginWindow(),
                properties.loginMaxTrackedClients(), clock);
    }

    @Bean(ACCOUNT_LIMITER)
    LoginAttemptLimiter accountLoginAttemptLimiter(AuthProperties properties, Clock clock) {
        return new InMemoryLoginRateLimiter(properties.loginAccountMaxAttempts(),
                properties.loginAccountWindow(), properties.loginMaxTrackedAccounts(), clock);
    }
}
