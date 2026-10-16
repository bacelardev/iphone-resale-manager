package io.github.bacelardev.iphoneresale.config;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;
import io.github.bacelardev.iphoneresale.config.properties.AuthProperties;
import io.github.bacelardev.iphoneresale.infrastructure.security.InMemoryLoginRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Three independent login limiters: per resolved client address (applied by the login filter), per
 * normalized account and client, and per normalized account (both applied by the login throttle).
 */
@Configuration(proxyBeanMethods = false)
public class LoginRateLimitConfiguration {

    public static final String CLIENT_LIMITER = "clientLoginAttemptLimiter";
    public static final String ACCOUNT_CLIENT_LIMITER = "accountClientLoginAttemptLimiter";
    public static final String ACCOUNT_LIMITER = "accountLoginAttemptLimiter";

    @Bean(CLIENT_LIMITER)
    LoginAttemptLimiter clientLoginAttemptLimiter(AuthProperties properties, Clock clock) {
        return new InMemoryLoginRateLimiter(properties.loginMaxAttempts(), properties.loginWindow(),
                properties.loginMaxTrackedClients(), clock);
    }

    @Bean(ACCOUNT_CLIENT_LIMITER)
    LoginAttemptLimiter accountClientLoginAttemptLimiter(AuthProperties properties, Clock clock) {
        return new InMemoryLoginRateLimiter(properties.loginAccountClientMaxFailures(),
                properties.loginAccountClientWindow(), properties.loginMaxTrackedAccounts(), clock);
    }

    @Bean(ACCOUNT_LIMITER)
    LoginAttemptLimiter accountLoginAttemptLimiter(AuthProperties properties, Clock clock) {
        return new InMemoryLoginRateLimiter(properties.loginAccountMaxFailures(),
                properties.loginAccountWindow(), properties.loginMaxTrackedAccounts(), clock);
    }
}
