package com.example.loanplatform.application;

import java.util.UUID;

public record IdempotencyClaim(
        UUID applicationId,
        String requestHash,
        boolean newlyClaimed) {
}

