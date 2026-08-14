package com.example.loanplatform.domain.event;

import com.example.loanplatform.domain.LoanApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Immutable fact emitted after any successful application state transition. */
public record LoanApplicationStatusChangedEvent(
        UUID eventId,
        UUID applicationId,
        LoanApplicationStatus previousStatus,
        LoanApplicationStatus currentStatus,
        long aggregateVersion,
        Instant occurredAt) implements LoanApplicationEvent {

    @Override
    public String eventType() {
        return "LoanApplicationStatusChanged";
    }
}
