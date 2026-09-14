package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.financial.FinancialService;
import io.github.bacelardev.iphoneresale.domain.enums.FinancialDirection;
import io.github.bacelardev.iphoneresale.domain.enums.FinancialTransactionType;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.AdjustmentRequest;
import io.github.bacelardev.iphoneresale.web.dto.financial.FinancialSummaryResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.FinancialTransactionResponse;
import io.github.bacelardev.iphoneresale.web.dto.financial.OwnerMovementRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/financial")
public class FinancialController {

    private final FinancialService service;

    public FinancialController(FinancialService service) {
        this.service = service;
    }

    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialTransactionResponse contribution(
            @Valid @RequestBody OwnerMovementRequest request
    ) {
        return service.contribution(request);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialTransactionResponse withdrawal(
            @Valid @RequestBody OwnerMovementRequest request
    ) {
        return service.withdrawal(request);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialTransactionResponse adjustment(
            @Valid @RequestBody AdjustmentRequest request
    ) {
        return service.adjustment(request);
    }

    @GetMapping("/transactions")
    public PageResponse<FinancialTransactionResponse> transactions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, name = "type") List<FinancialTransactionType> types,
            @RequestParam(required = false, name = "direction") List<FinancialDirection> directions,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.list(from, to, types, directions, page, size, sort);
    }

    @GetMapping("/summary")
    public FinancialSummaryResponse summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return service.summary(from, to);
    }
}
