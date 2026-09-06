package io.github.bacelardev.iphoneresale.web.filter;

import io.github.bacelardev.iphoneresale.infrastructure.security.InMemoryLoginRateLimiter;
import io.github.bacelardev.iphoneresale.web.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final InMemoryLoginRateLimiter rateLimiter;
    private final ApiErrorWriter errorWriter;

    public LoginRateLimitFilter(InMemoryLoginRateLimiter rateLimiter, ApiErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
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
        String clientAddress = request.getRemoteAddr();
        InMemoryLoginRateLimiter.Decision decision = rateLimiter.tryAcquire(clientAddress);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            errorWriter.write(request, response, 429, "LOGIN_RATE_LIMITED",
                    "Muitas tentativas de autenticação. Tente novamente mais tarde.");
            return;
        }

        filterChain.doFilter(request, response);
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            rateLimiter.reset(clientAddress);
        }
    }
}
