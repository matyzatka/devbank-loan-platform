package com.example.loanplatform.processing;

import java.time.Instant;
import java.util.UUID;

public interface PreprocessingResultRepository {

    void savePassed(UUID eventId, UUID applicationId, String details, Instant checkedAt);
}
