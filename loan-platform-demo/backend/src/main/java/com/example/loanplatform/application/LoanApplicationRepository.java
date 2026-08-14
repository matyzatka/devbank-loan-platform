package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplication;

import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository {

    void insert(LoanApplication application);

    void update(LoanApplication application, long expectedVersion);

    Optional<LoanApplication> findById(UUID id);
}

