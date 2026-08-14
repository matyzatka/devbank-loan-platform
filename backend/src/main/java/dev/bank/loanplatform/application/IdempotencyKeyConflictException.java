package dev.bank.loanplatform.application;

/** Indicates unsafe reuse of an existing idempotency key with different request semantics. */
public final class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key '%s' has already been used for a different request"
                .formatted(idempotencyKey));
    }
}
