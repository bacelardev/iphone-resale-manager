package io.github.bacelardev.iphoneresale.web.filter;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;
import io.github.bacelardev.iphoneresale.application.service.auth.LoginRateLimitedException;
import io.github.bacelardev.iphoneresale.config.LoginRateLimitConfiguration;
import io.github.bacelardev.iphoneresale.web.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final LoginAttemptLimiter rateLimiter;
    private final LoginClientAddressResolver clientAddressResolver;
    private final ApiErrorWriter errorWriter;

    public LoginRateLimitFilter(
            @Qualifier(LoginRateLimitConfiguration.CLIENT_LIMITER) LoginAttemptLimiter rateLimiter,
            LoginClientAddressResolver clientAddressResolver,
            ApiErrorWriter errorWriter
    ) {
        this.rateLimiter = rateLimiter;
        this.clientAddressResolver = clientAddressResolver;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_PATH.equals(request.getRequestURI()) || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientAddress = clientAddressResolver.resolve(request);
        LoginAttemptLimiter.Decision decision = rateLimiter.tryAcquire(clientAddress);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            errorWriter.write(request, response, 429, LoginRateLimitedException.CODE,
                    LoginRateLimitedException.MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            rateLimiter.reset(clientAddress);
        }
    }
}
