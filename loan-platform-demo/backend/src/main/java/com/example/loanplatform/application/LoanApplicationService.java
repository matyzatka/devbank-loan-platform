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

@Service
public class LoanApplicationService {

    private final LoanApplicationRepository applications;
    private final IdempotencyRepository idempotency;
    private final OutboxRepository outbox;
    private final Clock clock;

    public LoanApplicationService(
            LoanApplicationRepository applications,
            IdempotencyRepository idempotency,
            OutboxRepository outbox,
            Clock clock) {
        this.applications = applications;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public LoanApplication create(CreateLoanApplicationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var idempotencyKey = requireText(command.idempotencyKey(), "idempotencyKey");
        var requestHash = requestHash(command);
        var proposedId = UUID.randomUUID();
        var now = clock.instant();
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
        outbox.append(new LoanApplicationSubmittedEvent(
                UUID.randomUUID(),
                application.getId(),
                application.getCustomerId(),
                application.getAmount(),
                application.getCurrency().getCurrencyCode(),
                application.getCreatedAt()));
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
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        var normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return new LoanApplicationPage(
                applications.findAll(status, normalizedQuery, page * size, size),
                page,
                size,
                applications.count(status, normalizedQuery));
    }

    @Transactional
    public LoanApplication startReview(UUID applicationId) {
        return changeStatus(applicationId, application -> application.startReview(clock));
    }

    @Transactional
    public LoanApplication approve(UUID applicationId) {
        return changeStatus(applicationId, application -> application.approve(clock));
    }

    @Transactional
    public LoanApplication reject(UUID applicationId) {
        return changeStatus(applicationId, application -> application.reject(clock));
    }

    private LoanApplication changeStatus(
            UUID applicationId,
            Consumer<LoanApplication> transition) {
        var application = get(applicationId);
        var previousStatus = application.getStatus();
        var expectedVersion = application.getVersion();
        transition.accept(application);
        applications.update(application, expectedVersion);
        appendStatusChanged(application, previousStatus);
        return application;
    }

    private void appendStatusChanged(
            LoanApplication application,
            LoanApplicationStatus previousStatus) {
        outbox.append(new LoanApplicationStatusChangedEvent(
                UUID.randomUUID(),
                application.getId(),
                previousStatus,
                application.getStatus(),
                application.getVersion(),
                application.getUpdatedAt()));
    }

    private static String requestHash(CreateLoanApplicationCommand command) {
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
