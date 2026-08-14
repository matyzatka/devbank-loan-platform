package com.example.loanplatform.application;

import java.util.UUID;

public final class OptimisticLockingConflictException extends RuntimeException {

    public OptimisticLockingConflictException(UUID applicationId, long expectedVersion) {
        super("Loan application %s no longer has expected version %d"
                .formatted(applicationId, expectedVersion));
    }
}
