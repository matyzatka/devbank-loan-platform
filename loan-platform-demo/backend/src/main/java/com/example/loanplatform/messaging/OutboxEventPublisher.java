package com.example.loanplatform.messaging;

import com.example.loanplatform.application.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.example.loanplatform.configuration.CorrelationIds;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@Slf4j
@ConditionalOnProperty(
        name = "loan-platform.outbox.publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxEventPublisher {

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final MeterRegistry meterRegistry;

    public OutboxEventPublisher(
            OutboxRepository outbox,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${loan-platform.events.topic}") String topic,
            @Value("${loan-platform.outbox.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${loan-platform.outbox.fixed-delay:1000}")
    @Transactional
    public void publishBatch() {
        for (var event : outbox.lockUnpublished(batchSize)) {
            outbox.recordPublishAttempt(event.id());
            try {
                var record = new ProducerRecord<String, String>(
                        topic, event.aggregateId().toString(), event.payload());
                record.headers().add(
                        CorrelationIds.HEADER,
                        event.correlationId().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).join();
                outbox.markPublished(event.id(), clock.instant());
                meterRegistry.counter("loan.outbox.publish", "result", "success").increment();
            } catch (RuntimeException exception) {
                meterRegistry.counter("loan.outbox.publish", "result", "failure").increment();
                log.warn(
                        "Outbox publication failed: eventId={}, eventType={}, attempt={}",
                        event.id(),
                        event.eventType(),
                        event.publishAttempts() + 1,
                        exception);
            }
        }
    }
}
