package dev.bank.loanplatform.application;

import java.time.Instant;
import java.util.UUID;

/** Atomically binds a client idempotency key to one canonical request and aggregate ID. */
public interface IdempotencyRepository {

    IdempotencyClaim claim(
            String idempotencyKey,
            String requestHash,
            UUID proposedApplicationId,
            Instant createdAt);
}
