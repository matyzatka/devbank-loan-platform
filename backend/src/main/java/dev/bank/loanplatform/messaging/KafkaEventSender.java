package dev.bank.loanplatform.messaging;

import dev.bank.loanplatform.application.PendingOutboxEvent;
import dev.bank.loanplatform.configuration.CorrelationIds;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Kafka transport adapter; application ID is the key to preserve per-aggregate ordering. */
@Component
@ConditionalOnProperty(
        name = "loan-platform.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class KafkaEventSender implements EventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventSender(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, PendingOutboxEvent event) {
        var record = new ProducerRecord<String, String>(
                topic, event.aggregateId().toString(), event.payload());
        record.headers().add(
                CorrelationIds.HEADER,
                event.requestId().getBytes(StandardCharsets.UTF_8));
        // Blocking for acknowledgement keeps success inside the outbox transaction's decision.
        kafkaTemplate.send(record).join();
    }
}
