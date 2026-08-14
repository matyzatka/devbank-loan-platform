package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplication;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.example.loanplatform.domain.LoanApplicationStatus;

public interface LoanApplicationRepository {

    void insert(LoanApplication application);

    void update(LoanApplication application, long expectedVersion);

    Optional<LoanApplication> findById(UUID id);

    List<LoanApplication> findAll(LoanApplicationStatus status, String query, int offset, int limit);

    long count(LoanApplicationStatus status, String query);
}
