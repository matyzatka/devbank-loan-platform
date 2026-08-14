package com.example.loanplatform.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "loan-platform.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LoanApplicationEventConsumer {

    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LoanApplicationEventConsumer(
            ProcessedEventRepository processedEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @KafkaListener(topics = "${loan-platform.events.topic}")
    @Transactional
    public void consume(String payload) {
        var event = parse(payload);
        var eventId = requiredUuid(event, "eventId");
        if (!processedEvents.claim(eventId, clock.instant())) {
            return;
        }

        processedEvents.appendAuditEntry(
                eventId,
                requiredUuid(event, "applicationId"),
                requiredText(event, "eventType"),
                payload,
                clock.instant());
    }

    private com.fasterxml.jackson.databind.JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kafka event is not valid JSON", exception);
        }
    }

    private static UUID requiredUuid(com.fasterxml.jackson.databind.JsonNode event, String field) {
        return UUID.fromString(requiredText(event, field));
    }

    private static String requiredText(com.fasterxml.jackson.databind.JsonNode event, String field) {
        var value = event.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Kafka event field '%s' is required".formatted(field));
        }
        return value;
    }
}
