package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Immutable read model for one audited aggregate transition. */
public record StatusHistoryEntry(
        UUID id,
        LoanApplicationStatus previousStatus,
        LoanApplicationStatus newStatus,
        long applicationVersion,
        Instant changedAt,
        StatusChangeSource changedBy,
        String requestId,
        UUID eventId) {
}
