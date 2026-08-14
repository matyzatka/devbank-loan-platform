package com.example.loanplatform.domain;

import java.util.EnumSet;
import java.util.Set;

public enum LoanApplicationStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    private Set<LoanApplicationStatus> allowedTargets() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> EnumSet.noneOf(LoanApplicationStatus.class);
        };
    }

    public boolean canTransitionTo(LoanApplicationStatus target) {
        return target != null && allowedTargets().contains(target);
    }
}

