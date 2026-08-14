package com.example.loanplatform.api;

import com.example.loanplatform.application.CreateLoanApplicationCommand;
import com.example.loanplatform.application.LoanApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class LoanApplicationController {

    private final LoanApplicationService service;

    public LoanApplicationController(LoanApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a corporate loan application")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application created or original idempotent response returned"),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflicts with an earlier request")
    })
    public ResponseEntity<LoanApplicationResponse> create(
            @Parameter(required = true, description = "Unique key for safely retrying this request")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateLoanApplicationRequest request) {
        var application = service.create(new CreateLoanApplicationCommand(
                idempotencyKey,
                request.customerId(),
                request.amount(),
                parseCurrency(request.currency())));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(application.getId())
                .toUri();
        return ResponseEntity.created(location)
                .body(LoanApplicationResponse.from(application));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a loan application")
    public LoanApplicationResponse get(@PathVariable UUID id) {
        return LoanApplicationResponse.from(service.get(id));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Move a submitted application under review")
    public LoanApplicationResponse review(@PathVariable UUID id) {
        return LoanApplicationResponse.from(service.startReview(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an application under review")
    public LoanApplicationResponse approve(@PathVariable UUID id) {
        return LoanApplicationResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an application under review")
    public LoanApplicationResponse reject(@PathVariable UUID id) {
        return LoanApplicationResponse.from(service.reject(id));
    }

    private static Currency parseCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported currency code: " + currencyCode, exception);
        }
    }
}

