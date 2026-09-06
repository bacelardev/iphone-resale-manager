package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.dto.auth.AuthSessionData;
import io.github.bacelardev.iphoneresale.application.service.auth.AuthenticateAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String RAW_LOGOUT_TOKEN_ATTRIBUTE =
            BearerTokenAuthenticationFilter.class.getName() + ".rawLogoutToken";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final AuthenticateAccessTokenService authenticationService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public BearerTokenAuthenticationFilter(
            AuthenticateAccessTokenService authenticationService,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.authenticationService = authenticationService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (LOGIN_PATH.equals(request.getRequestURI()) && "POST".equals(request.getMethod()))
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean logout = LOGOUT_PATH.equals(request.getRequestURI()) && "POST".equals(request.getMethod());
        if (Collections.list(request.getHeaders("Authorization")).size() > 1) {
            reject(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.isBlank()) {
            if (logout) {
                reject(request, response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(request, response);
            return;
        }

        String rawToken = authorization.substring(7);
        if (!BearerTokenFormat.isValid(rawToken)) {
            reject(request, response);
            return;
        }

        if (logout) {
            request.setAttribute(RAW_LOGOUT_TOKEN_ATTRIBUTE, rawToken);
        }

        Optional<AuthSessionData> session = authenticationService.authenticate(rawToken);
        if (session.isEmpty()) {
            if (logout) {
                filterChain.doFilter(request, response);
            } else {
                reject(request, response);
            }
            return;
        }

        AuthSessionData authenticatedSession = session.orElseThrow();
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                authenticatedSession.user().id(),
                authenticatedSession.user().username(),
                authenticatedSession.user().role()
        );
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + principal.role().name())
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("Invalid bearer credential")
        );
    }
}
