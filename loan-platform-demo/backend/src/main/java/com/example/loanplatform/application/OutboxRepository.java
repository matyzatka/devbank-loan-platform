package com.example.loanplatform.application;

import com.example.loanplatform.domain.event.LoanApplicationEvent;

public interface OutboxRepository {

    void append(LoanApplicationEvent event);
}

