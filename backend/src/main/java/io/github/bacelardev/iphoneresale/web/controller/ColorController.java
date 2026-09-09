package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.catalog.CatalogService;
import io.github.bacelardev.iphoneresale.web.dto.catalog.CreateCatalogRequest;
import io.github.bacelardev.iphoneresale.web.dto.catalog.DeviceColorResponse;
import io.github.bacelardev.iphoneresale.web.dto.catalog.UpdateCatalogRequest;
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
@RequestMapping("/api/v1/colors")
public class ColorController {

    private final CatalogService service;

    public ColorController(CatalogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeviceColorResponse> create(
            @Valid @RequestBody CreateCatalogRequest request
    ) {
        DeviceColorResponse response = service.createColor(request);
        return ResponseEntity.created(URI.create("/api/v1/colors/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<DeviceColorResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.listColors(search, active, page, size, sort);
    }

    @GetMapping("/{id}")
    public DeviceColorResponse get(@PathVariable UUID id) {
        return service.getColor(id);
    }

    @PatchMapping("/{id}")
    public DeviceColorResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCatalogRequest request
    ) {
        return service.updateColor(id, request);
    }

    @PostMapping("/{id}/activate")
    public DeviceColorResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.setColorActive(id, request.expectedVersion(), true);
    }

    @PostMapping("/{id}/deactivate")
    public DeviceColorResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.setColorActive(id, request.expectedVersion(), false);
    }
}
