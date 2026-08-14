package com.example.loanplatform.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Validated wire contract for creating a corporate loan application. */
public record CreateLoanApplicationRequest(
        @NotBlank
        @Size(max = 100)
        String customerId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter uppercase currency code")
        String currency) {
}
