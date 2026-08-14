package dev.bank.loanplatform.application;

import java.math.BigDecimal;
import java.util.Currency;

/** Transport-neutral input for the create use case, including its client retry identity. */
public record CreateLoanApplicationCommand(
        String idempotencyKey,
        String customerId,
        BigDecimal amount,
        Currency currency) {
}
