package dev.bank.loanplatform.messaging;

import dev.bank.loanplatform.LoanPlatformApplication;
import dev.bank.loanplatform.application.CreateLoanApplicationCommand;
import dev.bank.loanplatform.application.LoanApplicationService;
import dev.bank.loanplatform.application.PendingOutboxEvent;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Testcontainers
@SpringBootTest(
        classes = LoanPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "loan-platform.kafka.enabled=false",
                "loan-platform.outbox.publisher-enabled=true"
        })
@Import(OutboxPublicationFailureTest.FakeSenderConfiguration.class)
class OutboxPublicationFailureTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private LoanApplicationService service;

    @Autowired
    private OutboxEventPublisher publisher;

    @Autowired
    private FailingEventSender sender;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void clearDatabase() {
        dsl.deleteFrom(table("loan_application_status_history")).execute();
        dsl.deleteFrom(table("loan_preprocessing_result")).execute();
        dsl.deleteFrom(table("outbox_event")).execute();
        dsl.deleteFrom(table("idempotency_record")).execute();
        dsl.deleteFrom(table("loan_application")).execute();
        sender.fail.set(true);
    }

    @Test
    void keepsCommittedEventPendingUntilPublicationRecovers() {
        var application = service.create(new CreateLoanApplicationCommand(
                "publisher-failure-001",
                "CORP-123",
                new BigDecimal("2500000.00"),
                Currency.getInstance("EUR")));

        assertThat(dsl.fetchCount(table("loan_application"))).isOne();
        assertThat(dsl.fetchCount(table("outbox_event"))).isOne();
        assertThat(published(application.getId())).isFalse();

        publisher.publishBatch();

        assertThat(published(application.getId())).isFalse();
        assertThat(attempts(application.getId())).isOne();

        sender.fail.set(false);
        publisher.publishBatch();

        assertThat(published(application.getId())).isTrue();
        assertThat(attempts(application.getId())).isEqualTo(2);
    }

    private boolean published(java.util.UUID applicationId) {
        return Boolean.TRUE.equals(dsl.select(field("published_at").isNotNull())
                .from(table("outbox_event"))
                .where(field("aggregate_id", java.util.UUID.class).eq(applicationId))
                .fetchSingle(0, Boolean.class));
    }

    private int attempts(java.util.UUID applicationId) {
        return dsl.select(field("publish_attempts", Integer.class))
                .from(table("outbox_event"))
                .where(field("aggregate_id", java.util.UUID.class).eq(applicationId))
                .fetchSingle(0, Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeSenderConfiguration {
        @Bean
        FailingEventSender eventSender() {
            return new FailingEventSender();
        }
    }

    static class FailingEventSender implements EventSender {
        private final AtomicBoolean fail = new AtomicBoolean(true);

        @Override
        public void send(String topic, PendingOutboxEvent event) {
            if (fail.get()) {
                throw new IllegalStateException("Simulated failure before publication acknowledgement");
            }
        }
    }
}
