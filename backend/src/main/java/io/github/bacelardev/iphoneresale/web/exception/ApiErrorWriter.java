package io.github.bacelardev.iphoneresale.web.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bacelardev.iphoneresale.web.dto.error.ApiErrorResponse;
import io.github.bacelardev.iphoneresale.web.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        UUID requestId = RequestIdFilter.requestId(request);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                clock.instant(),
                status,
                code,
                message,
                request.getRequestURI(),
                requestId,
                List.of()
        ));
    }
}
