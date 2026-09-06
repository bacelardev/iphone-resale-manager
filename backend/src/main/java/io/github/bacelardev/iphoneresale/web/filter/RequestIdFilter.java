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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";

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
        if (supplied == null || supplied.isBlank()) {
            requestId = UUID.randomUUID();
        } else {
            try {
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
