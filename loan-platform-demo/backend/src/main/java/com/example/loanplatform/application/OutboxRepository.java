package com.example.loanplatform.application;

import com.example.loanplatform.domain.event.LoanApplicationEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void append(LoanApplicationEvent event);

    List<PendingOutboxEvent> lockUnpublished(int batchSize);

    void recordPublishAttempt(UUID eventId);

    void markPublished(UUID eventId, Instant publishedAt);
}
