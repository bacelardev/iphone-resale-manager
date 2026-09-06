package io.github.bacelardev.iphoneresale.web.filter;

import io.github.bacelardev.iphoneresale.config.properties.CorsProperties;
import io.github.bacelardev.iphoneresale.web.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class StrictCorsFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    private static final Set<String> ALLOWED_HEADERS =
            Set.of("authorization", "content-type", "x-request-id");

    private final CorsProperties properties;
    private final ApiErrorWriter errorWriter;

    public StrictCorsFilter(CorsProperties properties, ApiErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || request.getHeader(HttpHeaders.ORIGIN) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean preflight = "OPTIONS".equals(request.getMethod())
                && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;

        if (!properties.allowedOrigins().contains(origin)
                || !ALLOWED_METHODS.contains(preflight
                        ? request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD).toUpperCase(Locale.ROOT)
                        : request.getMethod())
                || (preflight && !requestedHeadersAreAllowed(request))) {
            errorWriter.write(request, response, 403, "CORS_REJECTED",
                    "A origem ou a operação cross-origin não é permitida.");
            return;
        }

        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, X-Request-Id");
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);

        if (preflight) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    "GET, POST, PATCH, DELETE, OPTIONS");
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    "Authorization, Content-Type, X-Request-Id");
            response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requestedHeadersAreAllowed(HttpServletRequest request) {
        String requestedHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (requestedHeaders == null || requestedHeaders.isBlank()) {
            return true;
        }
        return requestedHeaders.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::trim)
                .map(header -> header.toLowerCase(Locale.ROOT))
                .allMatch(ALLOWED_HEADERS::contains);
    }
}
