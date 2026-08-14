package com.example.loanplatform.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class LoanApplication {

    private final UUID id;
    private final String customerId;
    private final BigDecimal amount;
    private final Currency currency;
    private final Instant createdAt;

    private LoanApplicationStatus status;
    private long version;
    private Instant updatedAt;

    private LoanApplication(
            UUID id,
            String customerId,
            BigDecimal amount,
            Currency currency,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.customerId = requireText(customerId, "customerId");
        this.amount = requirePositive(amount);
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.status = LoanApplicationStatus.SUBMITTED;
        this.version = 0;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static LoanApplication submit(
            UUID id,
            String customerId,
            BigDecimal amount,
            Currency currency,
            Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new LoanApplication(id, customerId, amount, currency, clock.instant());
    }

    public static LoanApplication restore(
            UUID id,
            String customerId,
            BigDecimal amount,
            Currency currency,
            LoanApplicationStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        var application = new LoanApplication(id, customerId, amount, currency, createdAt);
        application.status = Objects.requireNonNull(status, "status must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        application.version = version;
        application.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        return application;
    }

    public void startReview(Clock clock) {
        transitionTo(LoanApplicationStatus.UNDER_REVIEW, clock);
    }

    public void approve(Clock clock) {
        transitionTo(LoanApplicationStatus.APPROVED, clock);
    }

    public void reject(Clock clock) {
        transitionTo(LoanApplicationStatus.REJECTED, clock);
    }

    private void transitionTo(LoanApplicationStatus targetStatus, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (!status.canTransitionTo(targetStatus)) {
            throw new InvalidLoanApplicationTransitionException(status, targetStatus);
        }

        status = targetStatus;
        version++;
        updatedAt = clock.instant();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return amount;
    }
}
