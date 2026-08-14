package dev.bank.loanplatform.messaging;

import dev.bank.loanplatform.application.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

import java.time.Clock;

/**
 * Polls committed outbox rows and delivers them to the configured event transport.
 * Publication is deliberately at least once: a crash after broker acknowledgement but before
 * {@code published_at} commits causes a safe duplicate that consumers must deduplicate.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "loan-platform.outbox.publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxEventPublisher {

    private final OutboxRepository outbox;
    private final EventSender eventSender;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final MeterRegistry meterRegistry;

    public OutboxEventPublisher(
            OutboxRepository outbox,
            EventSender eventSender,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${loan-platform.events.topic}") String topic,
            @Value("${loan-platform.outbox.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.eventSender = eventSender;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    /** Publishes one locked batch; row locking allows multiple publisher instances to share work safely. */
    @Scheduled(fixedDelayString = "${loan-platform.outbox.fixed-delay:1000}")
    @Transactional
    public void publishBatch() {
        for (var event : outbox.lockUnpublished(batchSize)) {
            try (var ignoredRequest = MDC.putCloseable("requestId", event.requestId());
                 var ignoredApplication = MDC.putCloseable("applicationId", event.aggregateId().toString());
                 var ignoredEvent = MDC.putCloseable("eventId", event.id().toString())) {
                // Count the attempt before I/O so operational data also reflects failed sends.
                outbox.recordPublishAttempt(event.id());
                try {
                    eventSender.send(topic, event);
                    outbox.markPublished(event.id(), clock.instant());
                    meterRegistry.counter("loan.outbox.publish", "result", "success").increment();
                } catch (RuntimeException exception) {
                    meterRegistry.counter("loan.outbox.publish", "result", "failure").increment();
                    log.warn(
                            "Outbox event publication failed: eventType={}, attempt={}",
                            event.eventType(),
                            event.publishAttempts() + 1,
                            exception);
                }
            }
        }
    }
}
