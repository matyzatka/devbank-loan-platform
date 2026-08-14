package com.example.loanplatform.api;

import com.example.loanplatform.domain.LoanApplication;
import com.example.loanplatform.domain.LoanApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanApplicationResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        LoanApplicationStatus status,
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
                application.getVersion(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}

