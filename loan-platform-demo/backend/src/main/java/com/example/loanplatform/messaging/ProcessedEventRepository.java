package com.example.loanplatform.messaging;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository {

    boolean claim(UUID eventId, Instant processedAt);

    void appendAuditEntry(
            UUID eventId,
            UUID applicationId,
            String eventType,
            String payload,
            String correlationId,
            Instant receivedAt);
}
