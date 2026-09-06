package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.web.exception.ApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        response.setHeader("WWW-Authenticate", "Bearer realm=\"iphone-resale\"");
        errorWriter.write(request, response, 401, "UNAUTHORIZED",
                "Autenticação válida é obrigatória para acessar este recurso.");
    }
}
