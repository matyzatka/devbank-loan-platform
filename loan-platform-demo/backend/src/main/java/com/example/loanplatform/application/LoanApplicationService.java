package com.example.loanplatform.application;

import com.example.loanplatform.domain.LoanApplication;
import com.example.loanplatform.domain.LoanApplicationStatus;
import com.example.loanplatform.domain.event.LoanApplicationStatusChangedEvent;
import com.example.loanplatform.domain.event.LoanApplicationSubmittedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Coordinates loan-application use cases and defines their database transaction boundaries.
 * Domain objects enforce legal transitions; this service makes each transition atomic with its
 * audit record and outbox event so externally visible state can never outrun event delivery.
 */
@Service
public class LoanApplicationService {

    private final LoanApplicationRepository applications;
    private final IdempotencyRepository idempotency;
    private final OutboxRepository outbox;
    private final StatusHistoryRepository statusHistory;
    private final RequestIdProvider requestIds;
    private final Clock clock;

    public LoanApplicationService(
            LoanApplicationRepository applications,
            IdempotencyRepository idempotency,
            OutboxRepository outbox,
            StatusHistoryRepository statusHistory,
            RequestIdProvider requestIds,
            Clock clock) {
        this.applications = applications;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.statusHistory = statusHistory;
        this.requestIds = requestIds;
        this.clock = clock;
    }

    /**
     * Creates an application once for a stable idempotency key.
     * Replays return the original aggregate, while reuse with different canonical input is rejected.
     */
    @Transactional
    public LoanApplication create(CreateLoanApplicationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var idempotencyKey = requireText(command.idempotencyKey(), "idempotencyKey");
        var requestHash = requestHash(command);
        var proposedId = UUID.randomUUID();
        var now = clock.instant();
        // The database claim is the concurrency boundary: only its winning transaction may create state.
        var claim = idempotency.claim(idempotencyKey, requestHash, proposedId, now);

        if (!claim.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(idempotencyKey);
        }
        if (!claim.newlyClaimed()) {
            return get(claim.applicationId());
        }

        var application = LoanApplication.submit(
                claim.applicationId(),
                command.customerId(),
                command.amount(),
                command.currency(),
                clock);
        applications.insert(application);
        var eventId = UUID.randomUUID();
        outbox.append(new LoanApplicationSubmittedEvent(
                eventId,
                application.getId(),
                application.getCustomerId(),
                application.getAmount(),
                application.getCurrency().getCurrencyCode(),
                application.getCreatedAt()));
        statusHistory.append(
                application.getId(), null, application.getStatus(), application.getVersion(),
                application.getCreatedAt(), StatusChangeSource.API,
                requestIds.currentOrGenerate(), eventId);
        return application;
    }

    @Transactional(readOnly = true)
    public LoanApplication get(UUID applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new LoanApplicationNotFoundException(applicationId));
    }

    @Transactional(readOnly = true)
    public LoanApplicationPage list(LoanApplicationStatus status, String query, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        var normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return new LoanApplicationPage(
                applications.findAll(status, normalizedQuery, page * size, size),
                page,
                size,
                applications.count(status, normalizedQuery));
    }

    /** Worker-only entry point; the submitted event is retained as the causal audit identifier. */
    @Transactional
    public LoanApplication startReviewFromWorker(
            UUID applicationId,
            String requestId,
            UUID consumedEventId) {
        return changeStatus(
                applicationId,
                application -> application.startReview(clock),
                StatusChangeSource.WORKER,
                requestId,
                consumedEventId,
                null);
    }

    @Transactional
    public LoanApplication approve(UUID applicationId, long expectedVersion) {
        return changeStatus(
                applicationId,
                application -> application.approve(clock),
                StatusChangeSource.API,
                requestIds.currentOrGenerate(),
                null,
                expectedVersion);
    }

    @Transactional
    public LoanApplication reject(UUID applicationId, long expectedVersion) {
        return changeStatus(
                applicationId,
                application -> application.reject(clock),
                StatusChangeSource.API,
                requestIds.currentOrGenerate(),
                null,
                expectedVersion);
    }

    private LoanApplication changeStatus(
            UUID applicationId,
            Consumer<LoanApplication> transition,
            StatusChangeSource source,
            String requestId,
            UUID causationEventId,
            Long requestedVersion) {
        // State, optimistic-lock update, audit history, and outbox append share this transaction.
        var application = get(applicationId);
        var previousStatus = application.getStatus();
        var expectedVersion = application.getVersion();
        if (requestedVersion != null && requestedVersion != expectedVersion) {
            throw new OptimisticLockingConflictException(applicationId, requestedVersion);
        }
        transition.accept(application);
        applications.update(application, expectedVersion);
        var emittedEventId = appendStatusChanged(application, previousStatus);
        statusHistory.append(
                application.getId(), previousStatus, application.getStatus(), application.getVersion(),
                application.getUpdatedAt(), source, requestId,
                causationEventId == null ? emittedEventId : causationEventId);
        return application;
    }

    private UUID appendStatusChanged(
            LoanApplication application,
            LoanApplicationStatus previousStatus) {
        var eventId = UUID.randomUUID();
        outbox.append(new LoanApplicationStatusChangedEvent(
                eventId,
                application.getId(),
                previousStatus,
                application.getStatus(),
                application.getVersion(),
                application.getUpdatedAt()));
        return eventId;
    }

    private static String requestHash(CreateLoanApplicationCommand command) {
        // Canonical decimal formatting prevents semantically equal values (10 and 10.0) from conflicting.
        var canonicalRequest = "%s\n%s\n%s"
                .formatted(
                        command.customerId(),
                        command.amount() == null ? null : command.amount().stripTrailingZeros().toPlainString(),
                        command.currency() == null ? null : command.currency().getCurrencyCode());
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
