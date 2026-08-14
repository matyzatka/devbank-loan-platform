package dev.bank.loanplatform.api;

import dev.bank.loanplatform.application.CreateLoanApplicationCommand;
import dev.bank.loanplatform.application.LoanApplicationService;
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
import dev.bank.loanplatform.domain.LoanApplicationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Currency;
import java.util.UUID;

/**
 * HTTP adapter for operator-facing loan commands and queries.
 * It is absent from the worker runtime, preserving the intended process boundary in one artifact.
 */
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
    @Operation(summary = "Založit žádost o korporátní úvěr")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Žádost byla vytvořena nebo vrácena z idempotentního požadavku"),
            @ApiResponse(responseCode = "400", description = "Validace požadavku selhala"),
            @ApiResponse(responseCode = "409", description = "Idempotency key koliduje s dřívějším požadavkem")
    })
    public ResponseEntity<LoanApplicationResponse> create(
            @Parameter(required = true, description = "Unikátní klíč pro bezpečné opakování požadavku")
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
    @Operation(summary = "Načíst detail úvěrové žádosti")
    public LoanApplicationResponse get(@PathVariable UUID id) {
        return LoanApplicationResponse.from(service.get(id));
    }

    @GetMapping("/{id}/processing")
    @Operation(summary = "Načíst výsledek předběžné kontroly a auditní historii")
    public ApplicationProcessingResponse getProcessing(@PathVariable UUID id) {
        try (var ignored = MDC.putCloseable("applicationId", id.toString())) {
            return ApplicationProcessingResponse.from(service.getProcessingDetails(id));
        }
    }

    @GetMapping
    @Operation(summary = "Vypsat a filtrovat úvěrové žádosti")
    public LoanApplicationPageResponse list(
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return LoanApplicationPageResponse.from(service.list(status, query, page, size));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Schválit posuzovanou žádost")
    public LoanApplicationResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionLoanApplicationRequest request) {
        try (var ignored = MDC.putCloseable("applicationId", id.toString())) {
            return transitionCompleted(service.approve(id, request.expectedVersion()));
        }
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Zamítnout posuzovanou žádost")
    public LoanApplicationResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionLoanApplicationRequest request) {
        try (var ignored = MDC.putCloseable("applicationId", id.toString())) {
            return transitionCompleted(service.reject(id, request.expectedVersion(), request.reason()));
        }
    }

    private static LoanApplicationResponse transitionCompleted(
            dev.bank.loanplatform.domain.LoanApplication application) {
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
