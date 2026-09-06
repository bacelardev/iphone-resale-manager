package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.dto.auth.IssuedAccessToken;
import io.github.bacelardev.iphoneresale.application.service.auth.AuthenticateUserService;
import io.github.bacelardev.iphoneresale.application.service.auth.GetCurrentUserService;
import io.github.bacelardev.iphoneresale.application.service.auth.LogoutUserService;
import io.github.bacelardev.iphoneresale.application.service.auth.UnauthenticatedException;
import io.github.bacelardev.iphoneresale.infrastructure.security.BearerTokenAuthenticationFilter;
import io.github.bacelardev.iphoneresale.web.dto.auth.LoginRequest;
import io.github.bacelardev.iphoneresale.web.dto.auth.LoginResponse;
import io.github.bacelardev.iphoneresale.web.dto.auth.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUserService authenticateUserService;
    private final GetCurrentUserService getCurrentUserService;
    private final LogoutUserService logoutUserService;

    public AuthController(
            AuthenticateUserService authenticateUserService,
            GetCurrentUserService getCurrentUserService,
            LogoutUserService logoutUserService
    ) {
        this.authenticateUserService = authenticateUserService;
        this.getCurrentUserService = getCurrentUserService;
        this.logoutUserService = logoutUserService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedAccessToken issued = authenticateUserService.authenticate(
                request.username(),
                request.password()
        );
        return new LoginResponse(
                issued.value(),
                "Bearer",
                issued.expiresAt(),
                UserResponse.from(issued.user())
        );
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(getCurrentUserService.getCurrentUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object rawToken = request.getAttribute(
                BearerTokenAuthenticationFilter.RAW_LOGOUT_TOKEN_ATTRIBUTE
        );
        if (!(rawToken instanceof String token)) {
            throw new UnauthenticatedException();
        }
        logoutUserService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
