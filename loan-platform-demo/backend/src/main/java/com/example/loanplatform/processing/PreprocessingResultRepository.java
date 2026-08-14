package com.example.loanplatform.processing;

import java.time.Instant;
import java.util.UUID;

/** Stores the durable outcome of the worker's preliminary validation and process check. */
public interface PreprocessingResultRepository {

    void savePassed(UUID eventId, UUID applicationId, String details, Instant checkedAt);
}
