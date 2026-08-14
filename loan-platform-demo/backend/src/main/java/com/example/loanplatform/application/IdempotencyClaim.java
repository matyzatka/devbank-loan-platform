package com.example.loanplatform.application;

import java.util.UUID;

/** Result of the atomic idempotency-key claim, including whether this transaction owns creation. */
public record IdempotencyClaim(
        UUID applicationId,
        String requestHash,
        boolean newlyClaimed) {
}
