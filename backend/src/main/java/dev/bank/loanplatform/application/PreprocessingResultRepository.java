package dev.bank.loanplatform.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves the worker's preliminary validation and process-check outcome. */
public interface PreprocessingResultRepository {

    Optional<PreprocessingResult> findByApplicationId(UUID applicationId);

    void savePassed(UUID eventId, UUID applicationId, String details, Instant checkedAt);
}
