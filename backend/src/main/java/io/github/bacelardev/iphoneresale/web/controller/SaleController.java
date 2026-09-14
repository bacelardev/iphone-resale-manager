package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.sale.SaleService;
import io.github.bacelardev.iphoneresale.web.dto.sale.CancelSaleRequest;
import io.github.bacelardev.iphoneresale.web.dto.sale.RegisterSaleRequest;
import io.github.bacelardev.iphoneresale.web.dto.sale.SaleResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/sale")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SaleResponse> register(
            @PathVariable UUID deviceId,
            @Valid @RequestBody RegisterSaleRequest request
    ) {
        SaleResponse response = service.register(deviceId, request);
        return ResponseEntity.created(URI.create("/api/v1/devices/" + deviceId + "/sale"))
                .body(response);
    }

    @GetMapping
    public SaleResponse get(@PathVariable UUID deviceId) {
        return service.getActive(deviceId);
    }

    @PostMapping("/cancel")
    public SaleResponse cancel(
            @PathVariable UUID deviceId,
            @Valid @RequestBody CancelSaleRequest request
    ) {
        return service.cancel(deviceId, request);
    }
}
