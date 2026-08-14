package com.example.loanplatform.persistence;

import com.example.loanplatform.application.OutboxRepository;
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
import java.time.ZoneOffset;
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

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public JooqOutboxRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(LoanApplicationEvent event) {
        dsl.insertInto(OUTBOX)
                .columns(ID, AGGREGATE_ID, EVENT_TYPE, SCHEMA_VERSION, PAYLOAD, OCCURRED_AT)
                .values(
                        event.eventId(),
                        event.applicationId(),
                        event.eventType(),
                        event.schemaVersion(),
                        JSONB.valueOf(toJson(event)),
                        event.occurredAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    private String toJson(LoanApplicationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event " + event.eventId(), exception);
        }
    }
}
