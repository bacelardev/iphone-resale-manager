package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.catalog.PartCatalogService;
import io.github.bacelardev.iphoneresale.web.dto.catalog.CreatePartRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.PartCatalogResponse;
import io.github.bacelardev.iphoneresale.web.dto.catalog.UpdatePartRequest;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.VersionRequest;
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

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parts")
public class PartController {

    private final PartCatalogService service;

    public PartController(PartCatalogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PartCatalogResponse> create(@Valid @RequestBody CreatePartRequest request) {
        PartCatalogResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/parts/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<PartCatalogResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.list(search, active, page, size, sort);
    }

    @GetMapping("/{id}")
    public PartCatalogResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public PartCatalogResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePartRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public PartCatalogResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.setActive(id, request.expectedVersion(), true);
    }

    @PostMapping("/{id}/deactivate")
    public PartCatalogResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.setActive(id, request.expectedVersion(), false);
    }
}
