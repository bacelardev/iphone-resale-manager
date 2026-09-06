package io.github.bacelardev.iphoneresale.web.filter;

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
import java.util.UUID;
import java.util.Collections;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ApiErrorWriter errorWriter;

    public RequestIdFilter(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER_NAME);
        UUID requestId;
        if (supplied == null) {
            requestId = UUID.randomUUID();
        } else {
            try {
                if (Collections.list(request.getHeaders(HEADER_NAME)).size() != 1
                        || !UUID_PATTERN.matcher(supplied).matches()) {
                    throw new IllegalArgumentException("Invalid request ID format");
                }
                requestId = UUID.fromString(supplied);
            } catch (IllegalArgumentException exception) {
                requestId = UUID.randomUUID();
                request.setAttribute(ATTRIBUTE_NAME, requestId);
                response.setHeader(HEADER_NAME, requestId.toString());
                errorWriter.write(request, response, 400, "INVALID_REQUEST_ID",
                        "O cabeçalho X-Request-Id deve conter um UUID válido.");
                return;
            }
        }

        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId.toString());
        filterChain.doFilter(request, response);
    }

    public static UUID requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE_NAME);
        return requestId instanceof UUID uuid ? uuid : UUID.randomUUID();
    }
}
