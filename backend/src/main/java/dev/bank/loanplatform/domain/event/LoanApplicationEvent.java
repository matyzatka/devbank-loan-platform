package dev.bank.loanplatform.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Closed family of versioned business events emitted by the loan aggregate workflow. */
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
