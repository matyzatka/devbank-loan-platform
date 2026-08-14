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
import org.springframework.web.bind.annotation.RequestParam;
import com.example.loanplatform.domain.LoanApplicationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@Slf4j
@ConditionalOnProperty(
        name = "loan-platform.api.enabled",
        havingValue = "true",
        matchIfMissing = true)
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
            @ApiResponse(responseCode = "409", description = "Idempotency key conflicts with a previous request")
    })
    public ResponseEntity<LoanApplicationResponse> create(
            @Parameter(required = true, description = "Unique key for safely retrying the request")
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
        try (var ignored = MDC.putCloseable("applicationId", application.getId().toString())) {
            log.info("Loan application submitted: status={}", application.getStatus());
        }
        return ResponseEntity.created(location)
                .body(LoanApplicationResponse.from(application));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a loan application")
    public LoanApplicationResponse get(@PathVariable UUID id) {
        return LoanApplicationResponse.from(service.get(id));
    }

    @GetMapping
    @Operation(summary = "List and filter loan applications")
    public LoanApplicationPageResponse list(
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return LoanApplicationPageResponse.from(service.list(status, query, page, size));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an application under review")
    public LoanApplicationResponse approve(@PathVariable UUID id) {
        return transitionCompleted(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an application under review")
    public LoanApplicationResponse reject(@PathVariable UUID id) {
        return transitionCompleted(service.reject(id));
    }

    private static LoanApplicationResponse transitionCompleted(
            com.example.loanplatform.domain.LoanApplication application) {
        try (var ignored = MDC.putCloseable("applicationId", application.getId().toString())) {
            log.info(
                    "Loan application status changed: status={}, version={}",
                    application.getStatus(),
                    application.getVersion());
        }
        return LoanApplicationResponse.from(application);
    }

    private static Currency parseCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported currency code: " + currencyCode, exception);
        }
    }
}
