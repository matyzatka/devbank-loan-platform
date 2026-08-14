package com.example.loanplatform.api;

import com.example.loanplatform.domain.LoanApplication;
import com.example.loanplatform.domain.LoanApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable API representation kept separate from the mutable domain aggregate. */
public record LoanApplicationResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        LoanApplicationStatus status,
        String rejectionReason,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static LoanApplicationResponse from(LoanApplication application) {
        return new LoanApplicationResponse(
                application.getId(),
                application.getCustomerId(),
                application.getAmount(),
                application.getCurrency().getCurrencyCode(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getVersion(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
