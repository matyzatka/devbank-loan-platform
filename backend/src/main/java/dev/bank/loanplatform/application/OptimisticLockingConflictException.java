package dev.bank.loanplatform.application;

import java.util.UUID;

/** Signals that another transaction changed the aggregate after it was read. */
public final class OptimisticLockingConflictException extends RuntimeException {

    public OptimisticLockingConflictException(UUID applicationId, long expectedVersion) {
        super("Loan application %s no longer has expected version %d"
                .formatted(applicationId, expectedVersion));
    }
}
