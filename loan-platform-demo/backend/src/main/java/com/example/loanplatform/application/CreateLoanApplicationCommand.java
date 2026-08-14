package com.example.loanplatform.application;

import java.math.BigDecimal;
import java.util.Currency;

public record CreateLoanApplicationCommand(
        String idempotencyKey,
        String customerId,
        BigDecimal amount,
        Currency currency) {
}

