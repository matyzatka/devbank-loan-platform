package com.example.loanplatform.application;

import java.time.Instant;
import java.util.UUID;

/** Immutable projection of an unpublished outbox row passed to the transport adapter. */
public record PendingOutboxEvent(
        UUID id,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant occurredAt,
        String requestId,
        int publishAttempts) {
}
