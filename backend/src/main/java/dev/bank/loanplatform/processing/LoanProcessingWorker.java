package dev.bank.loanplatform.processing;

import dev.bank.loanplatform.application.LoanApplicationService;
import dev.bank.loanplatform.application.PreprocessingResultRepository;
import dev.bank.loanplatform.configuration.CorrelationIds;
import dev.bank.loanplatform.messaging.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * Asynchronous process boundary for preliminary validation of submitted applications.
 * The component is enabled only in the worker runtime, preventing the REST process from consuming
 * events it produced. Processing is idempotent and all business side effects commit together.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "loan-platform.worker.enabled", havingValue = "true")
public class LoanProcessingWorker {

    private static final String SUBMITTED_EVENT = "LoanApplicationSubmitted";

    private final ProcessedEventRepository processedEvents;
    private final PreprocessingResultRepository preprocessingResults;
    private final LoanApplicationService applications;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LoanProcessingWorker(
            ProcessedEventRepository processedEvents,
            PreprocessingResultRepository preprocessingResults,
            LoanApplicationService applications,
            ObjectMapper objectMapper,
            Clock clock) {
        this.processedEvents = processedEvents;
        this.preprocessingResults = preprocessingResults;
        this.applications = applications;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Dispatches supported envelope types and establishes per-event diagnostic context.
     * Unknown event types intentionally remain no-ops because the shared topic also carries status events.
     */
    @KafkaListener(topics = "${loan-platform.events.topic}")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        var event = parse(record.value());
        if (!SUBMITTED_EVENT.equals(requiredText(event, "eventType"))) {
            return;
        }

        var eventId = requiredUuid(event, "eventId");
        var applicationId = requiredUuid(event, "applicationId");
        var requestId = requestId(record, eventId);
        try (var ignoredRequest = MDC.putCloseable(CorrelationIds.MDC_KEY, requestId);
             var ignoredApplication = MDC.putCloseable("applicationId", applicationId.toString());
             var ignoredEvent = MDC.putCloseable("eventId", eventId.toString())) {
            processSubmitted(event, record.value(), eventId, applicationId, requestId);
        }
    }

    private void processSubmitted(
            JsonNode event,
            String rawPayload,
            UUID eventId,
            UUID applicationId,
            String requestId) {
        // The unique insert is part of this transaction; rollback makes a failed delivery retryable.
        if (!processedEvents.claim(eventId, clock.instant())) {
            log.debug("Duplicate event skipped safely");
            return;
        }

        var application = applications.get(applicationId);
        validateConsistency(event.path("payload"), application);
        preprocessingResults.savePassed(
                eventId,
                applicationId,
                "Event and persisted application consistency verified; application is ready for manual review.",
                clock.instant());
        var reviewed = applications.startReviewFromWorker(applicationId, requestId, eventId);
        processedEvents.appendAuditEntry(
                eventId, applicationId, SUBMITTED_EVENT, rawPayload, requestId, clock.instant());
        log.info("Preliminary validation and process check completed: status={}", reviewed.getStatus());
    }

    private static void validateConsistency(
            JsonNode payload,
            dev.bank.loanplatform.domain.LoanApplication application) {
        var customerId = requiredText(payload, "customerId");
        var currency = requiredText(payload, "currency");
        var amount = payload.path("amount").decimalValue();
        if (!application.getCustomerId().equals(customerId)
                || !application.getCurrency().getCurrencyCode().equals(currency)
                || application.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException(
                    "LoanApplicationSubmitted event does not match the persisted application");
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kafka event is not valid JSON", exception);
        }
    }

    private static String requestId(ConsumerRecord<String, String> record, UUID eventId) {
        var header = record.headers().lastHeader(CorrelationIds.HEADER);
        // Event ID is a deterministic fallback for producers that do not propagate request context.
        return header == null
                ? eventId.toString()
                : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static UUID requiredUuid(JsonNode event, String field) {
        return UUID.fromString(requiredText(event, field));
    }

    private static String requiredText(JsonNode event, String field) {
        var value = event.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Kafka event requires field '%s'".formatted(field));
        }
        return value;
    }
}
