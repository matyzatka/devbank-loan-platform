package com.example.loanplatform.api;

import com.example.loanplatform.application.ApplicationProcessingDetails;
import com.example.loanplatform.application.StatusChangeSource;
import com.example.loanplatform.domain.LoanApplicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only API representation of preliminary processing and audited state transitions. */
public record ApplicationProcessingResponse(
        PreprocessingResponse preprocessing,
        List<StatusHistoryResponse> statusHistory) {

    public static ApplicationProcessingResponse from(ApplicationProcessingDetails details) {
        return new ApplicationProcessingResponse(
                details.preprocessing().map(PreprocessingResponse::from).orElse(null),
                details.statusHistory().stream().map(StatusHistoryResponse::from).toList());
    }

    public record PreprocessingResponse(
            UUID eventId,
            String result,
            String details,
            Instant checkedAt) {

        private static PreprocessingResponse from(
                com.example.loanplatform.application.PreprocessingResult result) {
            return new PreprocessingResponse(
                    result.eventId(), result.result(), result.details(), result.checkedAt());
        }
    }

    public record StatusHistoryResponse(
            UUID id,
            LoanApplicationStatus previousStatus,
            LoanApplicationStatus newStatus,
            long applicationVersion,
            Instant changedAt,
            StatusChangeSource changedBy,
            String requestId,
            UUID eventId) {

        private static StatusHistoryResponse from(
                com.example.loanplatform.application.StatusHistoryEntry entry) {
            return new StatusHistoryResponse(
                    entry.id(), entry.previousStatus(), entry.newStatus(), entry.applicationVersion(),
                    entry.changedAt(), entry.changedBy(), entry.requestId(), entry.eventId());
        }
    }
}
