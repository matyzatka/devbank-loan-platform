package com.example.loanplatform.messaging;

import com.example.loanplatform.LoanPlatformApplication;
import com.example.loanplatform.application.CreateLoanApplicationCommand;
import com.example.loanplatform.application.LoanApplicationService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.kafka.core.KafkaTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = LoanPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "loan-platform.outbox.fixed-delay=3600000",
                "loan-platform.worker.enabled=true",
                "spring.kafka.consumer.group-id=loan-platform-kafka-integration-test"
        })
class KafkaOutboxIntegrationTest {

    private static final String TOPIC = "loan-application-events";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @Autowired
    private LoanApplicationService service;

    @Autowired
    private OutboxEventPublisher publisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void clearDatabase() {
        dsl.deleteFrom(table("loan_application_event_log")).execute();
        dsl.deleteFrom(table("loan_application_status_history")).execute();
        dsl.deleteFrom(table("loan_preprocessing_result")).execute();
        dsl.deleteFrom(table("processed_event")).execute();
        dsl.deleteFrom(table("outbox_event")).execute();
        dsl.deleteFrom(table("idempotency_record")).execute();
        dsl.deleteFrom(table("loan_application")).execute();
    }

    @Test
    void publishesCommittedOutboxEventAndProcessesItOnce() throws Exception {
        var application = service.create(command("kafka-flow-001"));
        assertThat(unpublishedCount()).isOne();

        publisher.publishBatch();

        await(() -> rowCount("loan_preprocessing_result") == 1);
        assertThat(service.get(application.getId()).getStatus().name()).isEqualTo("UNDER_REVIEW");
        assertThat(rowCount("loan_application_event_log")).isOne();
        assertThat(rowCount("loan_application_status_history")).isEqualTo(2);
        assertThat(unpublishedCount()).isOne();
        assertThat(meterRegistry.find("loan.outbox.publish")
                .tag("result", "success")
                .counter().count()).isGreaterThanOrEqualTo(1);
        assertThat(dsl.fetchOne(
                        "select publish_attempts, published_at is not null as published "
                                + "from outbox_event where aggregate_id = ? and event_type = 'LoanApplicationSubmitted'",
                        application.getId()))
                .satisfies(record -> {
                    assertThat(record.get("publish_attempts", Integer.class)).isOne();
                    assertThat(record.get("published", Boolean.class)).isTrue();
                });
        assertThat(dsl.fetchOne(
                        "select request_id from loan_application_event_log where application_id = ?",
                        application.getId())
                .get("request_id", String.class))
                .isNotBlank();

        var payload = outboxPayload(application.getId());
        kafkaTemplate.send(TOPIC, application.getId().toString(), payload).get();
        kafkaTemplate.send(TOPIC, application.getId().toString(), payload).get();

        await(() -> rowCount("processed_event") == 1);
        assertThat(rowCount("loan_preprocessing_result")).isOne();
        assertThat(rowCount("loan_application_event_log")).isOne();
        assertThat(rowCount("loan_application_status_history")).isEqualTo(2);
        assertThat(service.get(application.getId()).getVersion()).isOne();
    }

    @Test
    void retriesMalformedEventAndRoutesItToDeadLetterTopic() throws Exception {
        try (var dltConsumer = dltConsumer()) {
            dltConsumer.subscribe(List.of(TOPIC + ".DLT"));
            kafkaTemplate.send(TOPIC, "invalid-event", "not-json").get();

            var received = pollForRecord(dltConsumer);

            assertThat(received).isEqualTo("not-json");
            assertThat(rowCount("processed_event")).isZero();
            assertThat(rowCount("loan_application_event_log")).isZero();
        }
    }

    private KafkaConsumer<String, String> dltConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private static String pollForRecord(KafkaConsumer<String, String> consumer) {
        var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                return records.iterator().next().value();
            }
        }
        throw new AssertionError("No dead-letter record received within timeout");
    }

    private String outboxPayload(UUID applicationId) {
        var payloadField = field("payload", JSONB.class);
        return dsl.select(payloadField)
                .from(table("outbox_event"))
                .where(field("aggregate_id", UUID.class).eq(applicationId))
                .and(field("event_type", String.class).eq("LoanApplicationSubmitted"))
                .fetchOne(payloadField)
                .data();
    }

    private int unpublishedCount() {
        return dsl.fetchCount(
                table("outbox_event"),
                field("published_at").isNull());
    }

    private int rowCount(String tableName) {
        return dsl.fetchCount(table(tableName));
    }

    private static CreateLoanApplicationCommand command(String key) {
        return new CreateLoanApplicationCommand(
                key,
                "CORP-123",
                new BigDecimal("2500000.00"),
                Currency.getInstance("EUR"));
    }

    private static void await(CheckedCondition condition) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.evaluate()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Condition was not met within timeout");
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean evaluate() throws Exception;
    }
}
