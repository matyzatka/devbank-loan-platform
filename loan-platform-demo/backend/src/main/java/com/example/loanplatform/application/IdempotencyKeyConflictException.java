package com.example.loanplatform.application;

public final class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key '%s' has already been used for a different request"
                .formatted(idempotencyKey));
    }
}
