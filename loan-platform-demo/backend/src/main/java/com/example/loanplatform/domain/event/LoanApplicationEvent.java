package com.example.loanplatform.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface LoanApplicationEvent
        permits LoanApplicationSubmittedEvent, LoanApplicationStatusChangedEvent {

    UUID eventId();

    UUID applicationId();

    Instant occurredAt();

    default int schemaVersion() {
        return 1;
    }

    String eventType();
}

