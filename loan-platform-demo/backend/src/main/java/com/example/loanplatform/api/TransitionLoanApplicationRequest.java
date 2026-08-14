package com.example.loanplatform.api;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Command contract carrying the aggregate version observed by the operator.
 * Requiring it prevents a stale screen from silently deciding a newer application state.
 */
public record TransitionLoanApplicationRequest(
        @PositiveOrZero long expectedVersion) {
}
