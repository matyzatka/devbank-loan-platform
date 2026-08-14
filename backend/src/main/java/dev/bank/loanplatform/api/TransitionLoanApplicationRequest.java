package dev.bank.loanplatform.api;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Command contract carrying the aggregate version observed by the operator.
 * Requiring it prevents a stale screen from silently deciding a newer application state.
 */
public record TransitionLoanApplicationRequest(
        @PositiveOrZero long expectedVersion,
        @Size(max = 500, message = "Důvod může obsahovat nejvýše 500 znaků.") String reason) {
}
