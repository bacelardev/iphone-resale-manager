package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.initialization.BusinessInitializationService;
import io.github.bacelardev.iphoneresale.web.dto.initialization.BusinessInitializationPreviewResponse;
import io.github.bacelardev.iphoneresale.web.dto.initialization.BusinessInitializationResponse;
import io.github.bacelardev.iphoneresale.web.dto.initialization.CompleteBusinessInitializationRequest;
import io.github.bacelardev.iphoneresale.web.dto.initialization.StartBusinessInitializationRequest;
import io.github.bacelardev.iphoneresale.web.dto.initialization.UpdateBusinessInitializationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-initialization")
public class BusinessInitializationController {

    private final BusinessInitializationService service;

    public BusinessInitializationController(BusinessInitializationService service) {
        this.service = service;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessInitializationResponse start(
            @Valid @RequestBody StartBusinessInitializationRequest request
    ) {
        return service.start(request);
    }

    @GetMapping
    public BusinessInitializationResponse get() {
        return service.get();
    }

    @PatchMapping
    public BusinessInitializationResponse update(
            @Valid @RequestBody UpdateBusinessInitializationRequest request
    ) {
        return service.update(request);
    }

    @PostMapping("/complete")
    public BusinessInitializationResponse complete(
            @Valid @RequestBody CompleteBusinessInitializationRequest request
    ) {
        return service.complete(request);
    }

    @GetMapping("/preview")
    public BusinessInitializationPreviewResponse preview() {
        return service.preview();
    }
}
