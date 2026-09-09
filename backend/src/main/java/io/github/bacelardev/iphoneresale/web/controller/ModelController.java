package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.catalog.CatalogService;
import io.github.bacelardev.iphoneresale.web.dto.catalog.CreateModelRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.IphoneModelResponse;
import io.github.bacelardev.iphoneresale.web.dto.catalog.UpdateModelRequest;
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
@RequestMapping("/api/v1/models")
public class ModelController {

    private final CatalogService service;

    public ModelController(CatalogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IphoneModelResponse> create(@Valid @RequestBody CreateModelRequest request) {
        IphoneModelResponse response = service.createModel(request);
        return ResponseEntity.created(URI.create("/api/v1/models/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<IphoneModelResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.listModels(search, active, page, size, sort);
    }

    @GetMapping("/{id}")
    public IphoneModelResponse get(@PathVariable UUID id) {
        return service.getModel(id);
    }

    @PatchMapping("/{id}")
    public IphoneModelResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModelRequest request
    ) {
        return service.updateModel(id, request);
    }

    @PostMapping("/{id}/activate")
    public IphoneModelResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.setModelActive(id, request.expectedVersion(), true);
    }

    @PostMapping("/{id}/deactivate")
    public IphoneModelResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.setModelActive(id, request.expectedVersion(), false);
    }
}
