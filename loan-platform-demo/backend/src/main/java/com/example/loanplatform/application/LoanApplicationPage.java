package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplication;

import java.util.List;

public record LoanApplicationPage(
        List<LoanApplication> items,
        int page,
        int size,
        long totalElements) {

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
