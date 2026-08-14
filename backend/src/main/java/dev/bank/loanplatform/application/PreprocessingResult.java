package dev.bank.loanplatform.application;

import java.time.Instant;
import java.util.UUID;

/** Durable read model for the worker's preliminary validation and process check. */
public record PreprocessingResult(
        UUID eventId,
        String result,
        String details,
        Instant checkedAt) {
}
