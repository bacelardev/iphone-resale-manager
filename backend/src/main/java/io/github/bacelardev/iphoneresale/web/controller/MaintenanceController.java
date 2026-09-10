package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.maintenance.MaintenanceService;
import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.CancelMaintenanceRequest;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.MaintenanceDetailResponse;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.MaintenanceSummaryResponse;
import io.github.bacelardev.iphoneresale.web.dto.maintenance.RegisterMaintenanceRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/devices/{deviceId}/maintenances")
public class MaintenanceController {

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MaintenanceDetailResponse> register(
            @PathVariable UUID deviceId,
            @Valid @RequestBody RegisterMaintenanceRequest request
    ) {
        return created(deviceId, service.registerOperational(deviceId, request));
    }

    @PostMapping("/initial-import")
    public ResponseEntity<MaintenanceDetailResponse> initialImport(
            @PathVariable UUID deviceId,
            @Valid @RequestBody RegisterMaintenanceRequest request
    ) {
        return created(deviceId, service.registerInitialImport(deviceId, request));
    }

    @GetMapping
    public PageResponse<MaintenanceSummaryResponse> list(
            @PathVariable UUID deviceId,
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.list(deviceId, status, from, to, page, size, sort);
    }

    @GetMapping("/{maintenanceId}")
    public MaintenanceDetailResponse get(
            @PathVariable UUID deviceId,
            @PathVariable UUID maintenanceId
    ) {
        return service.get(deviceId, maintenanceId);
    }

    @PostMapping("/{maintenanceId}/cancel")
    public MaintenanceDetailResponse cancel(
            @PathVariable UUID deviceId,
            @PathVariable UUID maintenanceId,
            @Valid @RequestBody CancelMaintenanceRequest request
    ) {
        return service.cancel(deviceId, maintenanceId, request);
    }

    private static ResponseEntity<MaintenanceDetailResponse> created(
            UUID deviceId,
            MaintenanceDetailResponse response
    ) {
        return ResponseEntity.created(URI.create("/api/v1/devices/" + deviceId
                + "/maintenances/" + response.id())).body(response);
    }
}
