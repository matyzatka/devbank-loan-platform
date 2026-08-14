package com.example.loanplatform.persistence;

import com.example.loanplatform.messaging.ProcessedEventRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqProcessedEventRepository implements ProcessedEventRepository {

    private static final Table<Record> PROCESSED = table(name("processed_event"));
    private static final Field<UUID> PROCESSED_EVENT_ID = field(name("event_id"), UUID.class);
    private static final Field<OffsetDateTime> PROCESSED_AT = field(name("processed_at"), OffsetDateTime.class);

    private static final Table<Record> EVENT_LOG = table(name("loan_application_event_log"));
    private static final Field<UUID> LOG_EVENT_ID = field(name("event_id"), UUID.class);
    private static final Field<UUID> APPLICATION_ID = field(name("application_id"), UUID.class);
    private static final Field<String> EVENT_TYPE = field(name("event_type"), String.class);
    private static final Field<JSONB> PAYLOAD = field(name("payload"), JSONB.class);
    private static final Field<OffsetDateTime> RECEIVED_AT = field(name("received_at"), OffsetDateTime.class);
    private static final Field<String> CORRELATION_ID = field(name("correlation_id"), String.class);

    private final DSLContext dsl;

    public JooqProcessedEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean claim(UUID eventId, Instant processedAt) {
        return dsl.insertInto(PROCESSED)
                .columns(PROCESSED_EVENT_ID, PROCESSED_AT)
                .values(eventId, processedAt.atOffset(ZoneOffset.UTC))
                .onConflict(PROCESSED_EVENT_ID)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public void appendAuditEntry(
            UUID eventId,
            UUID applicationId,
            String eventType,
            String payload,
            String correlationId,
            Instant receivedAt) {
        dsl.insertInto(EVENT_LOG)
                .columns(LOG_EVENT_ID, APPLICATION_ID, EVENT_TYPE, PAYLOAD, CORRELATION_ID, RECEIVED_AT)
                .values(
                        eventId,
                        applicationId,
                        eventType,
                        JSONB.valueOf(payload),
                        correlationId,
                        receivedAt.atOffset(ZoneOffset.UTC))
                .execute();
    }
}
