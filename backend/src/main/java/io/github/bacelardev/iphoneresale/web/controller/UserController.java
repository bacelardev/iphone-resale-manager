package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.user.UserService;
import io.github.bacelardev.iphoneresale.web.dto.auth.UserResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.VersionRequest;
import io.github.bacelardev.iphoneresale.web.dto.user.CreateUserRequest;
import io.github.bacelardev.iphoneresale.web.dto.user.UpdateUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            UriComponentsBuilder uri
    ) {
        UserResponse response = service.create(request);
        return ResponseEntity.created(uri.path("/api/v1/users/{id}").build(response.id())).body(response);
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.list(search, active, page, size, sort);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public UserResponse activate(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.setActive(id, request.expectedVersion(), true);
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return service.setActive(id, request.expectedVersion(), false);
    }
}
