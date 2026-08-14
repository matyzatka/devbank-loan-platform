package com.example.loanplatform.application;

import java.util.UUID;

public final class LoanApplicationNotFoundException extends RuntimeException {

    public LoanApplicationNotFoundException(UUID applicationId) {
        super("Loan application %s was not found".formatted(applicationId));
    }
}

