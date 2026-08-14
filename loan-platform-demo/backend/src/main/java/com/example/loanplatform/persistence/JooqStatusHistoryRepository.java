package com.example.loanplatform.persistence;

import com.example.loanplatform.application.StatusChangeSource;
import com.example.loanplatform.application.StatusHistoryRepository;
import com.example.loanplatform.application.StatusHistoryEntry;
import com.example.loanplatform.domain.LoanApplicationStatus;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/** Append-only jOOQ adapter for business state-transition history. */
@Repository
public class JooqStatusHistoryRepository implements StatusHistoryRepository {

    private static final Table<Record> HISTORY = table(name("loan_application_status_history"));
    private static final Field<UUID> ID = field(name("id"), UUID.class);
    private static final Field<UUID> APPLICATION_ID = field(name("application_id"), UUID.class);
    private static final Field<String> PREVIOUS_STATUS = field(name("previous_status"), String.class);
    private static final Field<String> NEW_STATUS = field(name("new_status"), String.class);
    private static final Field<Long> APPLICATION_VERSION = field(name("application_version"), Long.class);
    private static final Field<OffsetDateTime> CHANGED_AT = field(name("changed_at"), OffsetDateTime.class);
    private static final Field<String> CHANGED_BY = field(name("changed_by"), String.class);
    private static final Field<String> REQUEST_ID = field(name("request_id"), String.class);
    private static final Field<UUID> EVENT_ID = field(name("event_id"), UUID.class);
    private static final Field<String> REASON = field(name("reason"), String.class);

    private final DSLContext dsl;

    public JooqStatusHistoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<StatusHistoryEntry> findByApplicationId(UUID applicationId) {
        return dsl.select(
                        ID, PREVIOUS_STATUS, NEW_STATUS, APPLICATION_VERSION,
                        CHANGED_AT, CHANGED_BY, REQUEST_ID, EVENT_ID, REASON)
                .from(HISTORY)
                .where(APPLICATION_ID.eq(applicationId))
                .orderBy(CHANGED_AT.asc(), APPLICATION_VERSION.asc())
                .fetch(record -> new StatusHistoryEntry(
                        record.get(ID),
                        record.get(PREVIOUS_STATUS) == null
                                ? null
                                : LoanApplicationStatus.valueOf(record.get(PREVIOUS_STATUS)),
                        LoanApplicationStatus.valueOf(record.get(NEW_STATUS)),
                        record.get(APPLICATION_VERSION),
                        record.get(CHANGED_AT).toInstant(),
                        StatusChangeSource.valueOf(record.get(CHANGED_BY)),
                        record.get(REQUEST_ID),
                        record.get(EVENT_ID),
                        record.get(REASON)));
    }

    @Override
    public void append(
            UUID applicationId,
            LoanApplicationStatus previousStatus,
            LoanApplicationStatus newStatus,
            long applicationVersion,
            Instant changedAt,
            StatusChangeSource source,
            String requestId,
            UUID eventId,
            String reason) {
        dsl.insertInto(HISTORY)
                .columns(
                        ID, APPLICATION_ID, PREVIOUS_STATUS, NEW_STATUS, APPLICATION_VERSION,
                        CHANGED_AT, CHANGED_BY, REQUEST_ID, EVENT_ID, REASON)
                .values(
                        UUID.randomUUID(), applicationId,
                        previousStatus == null ? null : previousStatus.name(), newStatus.name(),
                        applicationVersion, changedAt.atOffset(ZoneOffset.UTC), source.name(),
                        requestId, eventId, reason)
                .execute();
    }
}
