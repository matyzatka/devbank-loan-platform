package com.example.loanplatform.persistence;

import com.example.loanplatform.application.LoanApplicationRepository;
import com.example.loanplatform.application.LoanApplicationService;
import com.example.loanplatform.application.OptimisticLockingConflictException;
import com.example.loanplatform.application.CreateLoanApplicationCommand;
import com.example.loanplatform.application.IdempotencyKeyConflictException;
import com.example.loanplatform.application.LoanApplicationNotFoundException;
import com.example.loanplatform.LoanPlatformApplication;
import com.example.loanplatform.domain.LoanApplication;
import com.example.loanplatform.domain.LoanApplicationStatus;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Testcontainers
@SpringBootTest(
        classes = LoanPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "loan-platform.kafka.enabled=false",
                "loan-platform.outbox.publisher-enabled=false"
        })
class JooqLoanApplicationRepositoryTest {

    private static final Clock SUBMITTED_AT = fixedClock("2026-08-14T10:00:00Z");
    private static final Clock REVIEWED_AT = fixedClock("2026-08-14T11:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private LoanApplicationRepository repository;

    @Autowired
    private LoanApplicationService service;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void clearDatabase() {
        dsl.deleteFrom(table("loan_application_status_history")).execute();
        dsl.deleteFrom(table("loan_preprocessing_result")).execute();
        dsl.deleteFrom(table("outbox_event")).execute();
        dsl.deleteFrom(table("idempotency_record")).execute();
        dsl.deleteFrom(table("loan_application")).execute();
    }

    @Test
    void insertsAndRetrievesApplication() {
        var application = newApplication();

        repository.insert(application);

        var restored = repository.findById(application.getId()).orElseThrow();
        assertThat(restored.getId()).isEqualTo(application.getId());
        assertThat(restored.getCustomerId()).isEqualTo(application.getCustomerId());
        assertThat(restored.getAmount()).isEqualByComparingTo(application.getAmount());
        assertThat(restored.getCurrency()).isEqualTo(application.getCurrency());
        assertThat(restored.getStatus()).isEqualTo(LoanApplicationStatus.SUBMITTED);
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getCreatedAt()).isEqualTo(SUBMITTED_AT.instant());
    }

    @Test
    void updatesApplicationWhenVersionMatches() {
        var application = newApplication();
        repository.insert(application);
        application.startReview(REVIEWED_AT);

        repository.update(application, 0);

        var restored = repository.findById(application.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_REVIEW);
        assertThat(restored.getVersion()).isOne();
        assertThat(restored.getUpdatedAt()).isEqualTo(REVIEWED_AT.instant());
    }

    @Test
    void reportsOptimisticLockingConflictForStaleVersion() {
        var application = newApplication();
        repository.insert(application);
        application.startReview(REVIEWED_AT);
        repository.update(application, 0);
        application.approve(fixedClock("2026-08-14T12:00:00Z"));

        assertThatThrownBy(() -> repository.update(application, 0))
                .isInstanceOf(OptimisticLockingConflictException.class)
                .hasMessageContaining(application.getId().toString())
                .hasMessageContaining("version 0");
    }

    @Test
    void returnsEmptyForUnknownApplication() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void createsApplicationAndOutboxEventInOneUseCase() {
        var created = service.create(command("create-001", "2500000.00"));

        assertThat(created.getStatus()).isEqualTo(LoanApplicationStatus.SUBMITTED);
        assertThat(service.get(created.getId()).getId()).isEqualTo(created.getId());
        assertThat(rowCount("loan_application")).isOne();
        assertThat(rowCount("idempotency_record")).isOne();
        assertThat(rowCount("outbox_event")).isOne();
        var eventType = field("event_type", String.class);
        var aggregateId = field("aggregate_id", UUID.class);
        assertThat(dsl.select(eventType)
                .from(table("outbox_event"))
                .where(aggregateId.eq(created.getId()))
                .fetchOne(eventType))
                .isEqualTo("LoanApplicationSubmitted");
    }

    @Test
    void returnsOriginalApplicationForRepeatedIdempotentRequest() {
        var first = service.create(command("create-duplicate", "2500000.00"));
        var repeated = service.create(command("create-duplicate", "2500000.0"));

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(rowCount("loan_application")).isOne();
        assertThat(rowCount("outbox_event")).isOne();
    }

    @Test
    void rejectsIdempotencyKeyReusedForDifferentRequest() {
        service.create(command("reused-key", "2500000.00"));

        assertThatThrownBy(() -> service.create(command("reused-key", "2600000.00")))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining("reused-key");

        assertThat(rowCount("loan_application")).isOne();
        assertThat(rowCount("outbox_event")).isOne();
    }

    @Test
    void rollsBackIdempotencyClaimWhenCreationFails() {
        var invalid = new CreateLoanApplicationCommand(
                "rollback-key",
                "CORP-123",
                BigDecimal.ZERO,
                Currency.getInstance("EUR"));

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");

        assertThat(rowCount("idempotency_record")).isZero();
        assertThat(rowCount("loan_application")).isZero();
        assertThat(rowCount("outbox_event")).isZero();
    }

    @Test
    void movesApplicationThroughWorkflowAndCreatesOutboxEvents() {
        var created = service.create(command("workflow-001", "2500000.00"));

        var reviewed = service.startReviewFromWorker(
                created.getId(), "worker-test-request", UUID.randomUUID());
        var approved = service.approve(created.getId(), 1);

        assertThat(reviewed.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_REVIEW);
        assertThat(approved.getStatus()).isEqualTo(LoanApplicationStatus.APPROVED);
        assertThat(approved.getVersion()).isEqualTo(2);
        assertThat(rowCount("outbox_event")).isEqualTo(3);
        assertThat(rowCount("loan_application_status_history")).isEqualTo(3);
    }

    @Test
    void reportsMissingApplicationFromUseCase() {
        var unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(unknownId))
                .isInstanceOf(LoanApplicationNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    private int rowCount(String tableName) {
        return dsl.fetchCount(table(tableName));
    }

    private static CreateLoanApplicationCommand command(String key, String amount) {
        return new CreateLoanApplicationCommand(
                key,
                "CORP-123",
                new BigDecimal(amount),
                Currency.getInstance("EUR"));
    }

    private static LoanApplication newApplication() {
        return LoanApplication.submit(
                UUID.randomUUID(),
                "CORP-123",
                new BigDecimal("2500000.00"),
                Currency.getInstance("EUR"),
                SUBMITTED_AT);
    }

    private static Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
