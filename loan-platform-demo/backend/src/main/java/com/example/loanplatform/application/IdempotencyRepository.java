package com.example.loanplatform.application;

import java.time.Instant;
import java.util.UUID;

public interface IdempotencyRepository {

    IdempotencyClaim claim(
            String idempotencyKey,
            String requestHash,
            UUID proposedApplicationId,
            Instant createdAt);
}

