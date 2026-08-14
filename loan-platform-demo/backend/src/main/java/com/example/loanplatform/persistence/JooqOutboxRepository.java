package com.example.loanplatform.persistence;

import com.example.loanplatform.application.OutboxRepository;
import com.example.loanplatform.application.PendingOutboxEvent;
import com.example.loanplatform.configuration.CorrelationIds;
import com.example.loanplatform.domain.event.LoanApplicationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqOutboxRepository implements OutboxRepository {

    private static final Table<Record> OUTBOX = table(name("outbox_event"));
    private static final Field<UUID> ID = field(name("id"), UUID.class);
    private static final Field<UUID> AGGREGATE_ID = field(name("aggregate_id"), UUID.class);
    private static final Field<String> EVENT_TYPE = field(name("event_type"), String.class);
    private static final Field<Integer> SCHEMA_VERSION = field(name("schema_version"), Integer.class);
    private static final Field<JSONB> PAYLOAD = field(name("payload"), JSONB.class);
    private static final Field<OffsetDateTime> OCCURRED_AT = field(name("occurred_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> PUBLISHED_AT = field(name("published_at"), OffsetDateTime.class);
    private static final Field<Integer> PUBLISH_ATTEMPTS = field(name("publish_attempts"), Integer.class);
    private static final Field<String> REQUEST_ID = field(name("request_id"), String.class);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final CorrelationIds correlationIds;

    public JooqOutboxRepository(DSLContext dsl, ObjectMapper objectMapper, CorrelationIds correlationIds) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.correlationIds = correlationIds;
    }

    @Override
    public void append(LoanApplicationEvent event) {
        dsl.insertInto(OUTBOX)
                .columns(ID, AGGREGATE_ID, EVENT_TYPE, SCHEMA_VERSION, PAYLOAD, OCCURRED_AT, REQUEST_ID)
                .values(
                        event.eventId(),
                        event.applicationId(),
                        event.eventType(),
                        event.schemaVersion(),
                        JSONB.valueOf(toJson(event)),
                        event.occurredAt().atOffset(ZoneOffset.UTC),
                        correlationIds.currentOrGenerate())
                .execute();
    }

    @Override
    public List<PendingOutboxEvent> lockUnpublished(int batchSize) {
        return dsl.select(ID, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, OCCURRED_AT, REQUEST_ID, PUBLISH_ATTEMPTS)
                .from(OUTBOX)
                .where(PUBLISHED_AT.isNull())
                .orderBy(OCCURRED_AT, ID)
                .limit(batchSize)
                .forUpdate()
                .skipLocked()
                .fetch(record -> new PendingOutboxEvent(
                        record.get(ID),
                        record.get(AGGREGATE_ID),
                        record.get(EVENT_TYPE),
                        record.get(PAYLOAD).data(),
                        record.get(OCCURRED_AT).toInstant(),
                        record.get(REQUEST_ID),
                        record.get(PUBLISH_ATTEMPTS)));
    }

    @Override
    public void recordPublishAttempt(UUID eventId) {
        dsl.update(OUTBOX)
                .set(PUBLISH_ATTEMPTS, PUBLISH_ATTEMPTS.plus(1))
                .where(ID.eq(eventId))
                .execute();
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        dsl.update(OUTBOX)
                .set(PUBLISHED_AT, publishedAt.atOffset(ZoneOffset.UTC))
                .where(ID.eq(eventId))
                .execute();
    }

    @Override
    public long countUnpublished() {
        return dsl.fetchCount(OUTBOX, PUBLISHED_AT.isNull());
    }

    @Override
    public Instant oldestUnpublishedAt() {
        var value = dsl.select(org.jooq.impl.DSL.min(OCCURRED_AT))
                .from(OUTBOX)
                .where(PUBLISHED_AT.isNull())
                .fetchOne(0, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String toJson(LoanApplicationEvent event) {
        try {
            return objectMapper.writeValueAsString(new EventEnvelope(
                    event.eventId(),
                    event.applicationId(),
                    event.eventType(),
                    event.schemaVersion(),
                    event.occurredAt(),
                    event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event " + event.eventId(), exception);
        }
    }

    private record EventEnvelope(
            UUID eventId,
            UUID applicationId,
            String eventType,
            int eventVersion,
            Instant timestamp,
            LoanApplicationEvent payload) {
    }
}
