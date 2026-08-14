package dev.bank.loanplatform.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side idempotency port. A successful claim must participate in the same transaction as
 * business side effects so failures roll back the claim and remain retryable.
 */
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
