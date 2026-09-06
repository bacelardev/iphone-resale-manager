package io.github.bacelardev.iphoneresale.web.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "deve usar apenas letras, números, ponto, hífen ou sublinhado")
        String username,
        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {

    public LoginRequest {
        username = username == null ? null : username.trim();
    }

    @Override
    public String toString() {
        return "LoginRequest[username=[redacted], password=[redacted]]";
    }
}
