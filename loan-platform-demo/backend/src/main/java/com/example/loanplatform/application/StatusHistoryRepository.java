package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplicationStatus;

import java.time.Instant;
import java.util.UUID;

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
