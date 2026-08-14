package com.example.loanplatform.messaging;

import com.example.loanplatform.application.PendingOutboxEvent;
import com.example.loanplatform.configuration.CorrelationIds;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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
        kafkaTemplate.send(record).join();
    }
}
