package com.example.loanplatform.api;

import com.example.loanplatform.application.LoanApplicationPage;

import java.util.List;

public record LoanApplicationPageResponse(
        List<LoanApplicationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static LoanApplicationPageResponse from(LoanApplicationPage result) {
        return new LoanApplicationPageResponse(
                result.items().stream().map(LoanApplicationResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
