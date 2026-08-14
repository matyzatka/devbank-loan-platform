package com.example.loanplatform.application;

import com.example.loanplatform.domain.event.LoanApplicationEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional event-store port used by business transactions and the asynchronous publisher.
 * Implementations must lock unpublished batches so concurrent pollers cannot publish the same row concurrently.
 */
public interface OutboxRepository {

    void append(LoanApplicationEvent event);

    List<PendingOutboxEvent> lockUnpublished(int batchSize);

    void recordPublishAttempt(UUID eventId);

    void markPublished(UUID eventId, Instant publishedAt);

    long countUnpublished();

    Instant oldestUnpublishedAt();
}
