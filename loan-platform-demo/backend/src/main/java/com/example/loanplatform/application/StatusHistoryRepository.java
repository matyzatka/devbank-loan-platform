package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit port for recording who changed application state and what caused it. */
public interface StatusHistoryRepository {

    void append(
            UUID applicationId,
            LoanApplicationStatus previousStatus,
            LoanApplicationStatus newStatus,
            long applicationVersion,
            Instant changedAt,
            StatusChangeSource source,
            String requestId,
            UUID eventId);
}
