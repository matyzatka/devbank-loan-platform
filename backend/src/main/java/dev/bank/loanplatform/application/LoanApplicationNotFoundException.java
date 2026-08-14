package dev.bank.loanplatform.application;

import java.util.UUID;

/** Use-case failure raised when an application ID has no persisted aggregate. */
public final class LoanApplicationNotFoundException extends RuntimeException {

    public LoanApplicationNotFoundException(UUID applicationId) {
        super("Loan application %s was not found".formatted(applicationId));
    }
}
