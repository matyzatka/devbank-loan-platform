package dev.bank.loanplatform.application;

import dev.bank.loanplatform.domain.LoanApplication;

import java.util.List;

/** Application-layer page model independent of HTTP response formatting. */
public record LoanApplicationPage(
        List<LoanApplication> items,
        int page,
        int size,
        long totalElements) {

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
