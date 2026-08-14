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
        @NotBlank(message = "Zadejte název firemního klienta.")
        @Size(max = 100, message = "Název může obsahovat nejvýše 100 znaků.")
        String customerId,

        @NotNull(message = "Zadejte požadovanou částku.")
        @DecimalMin(value = "0.01", message = "Částka musí být vyšší než nula.")
        @Digits(integer = 17, fraction = 2, message = "Částka má neplatný formát.")
        BigDecimal amount,

        @NotBlank(message = "Vyberte měnu.")
        @Pattern(regexp = "[A-Z]{3}", message = "Měna musí být třípísmenný kód.")
        String currency) {
}
