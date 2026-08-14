package dev.bank.loanplatform.application;

import dev.bank.loanplatform.domain.LoanApplication;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import dev.bank.loanplatform.domain.LoanApplicationStatus;

/** Persistence port for aggregate storage and optimistic compare-and-set updates. */
public interface LoanApplicationRepository {

    void insert(LoanApplication application);

    void update(LoanApplication application, long expectedVersion);

    Optional<LoanApplication> findById(UUID id);

    List<LoanApplication> findAll(LoanApplicationStatus status, String query, int offset, int limit);

    long count(LoanApplicationStatus status, String query);
}
