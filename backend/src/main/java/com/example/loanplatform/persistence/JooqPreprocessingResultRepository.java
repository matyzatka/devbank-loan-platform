package com.example.loanplatform.persistence;

import com.example.loanplatform.application.PreprocessingResultRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Optional;
import com.example.loanplatform.application.PreprocessingResult;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/** Persists one durable worker outcome per submitted event. */
@Repository
public class JooqPreprocessingResultRepository implements PreprocessingResultRepository {

    private static final Table<Record> RESULT = table(name("loan_preprocessing_result"));
    private static final Field<UUID> EVENT_ID = field(name("event_id"), UUID.class);
    private static final Field<UUID> APPLICATION_ID = field(name("application_id"), UUID.class);
    private static final Field<String> RESULT_VALUE = field(name("result"), String.class);
    private static final Field<String> DETAILS = field(name("details"), String.class);
    private static final Field<OffsetDateTime> CHECKED_AT = field(name("checked_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqPreprocessingResultRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<PreprocessingResult> findByApplicationId(UUID applicationId) {
        return dsl.select(EVENT_ID, RESULT_VALUE, DETAILS, CHECKED_AT)
                .from(RESULT)
                .where(APPLICATION_ID.eq(applicationId))
                .orderBy(CHECKED_AT.desc())
                .limit(1)
                .fetchOptional(record -> new PreprocessingResult(
                        record.get(EVENT_ID),
                        record.get(RESULT_VALUE),
                        record.get(DETAILS),
                        record.get(CHECKED_AT).toInstant()));
    }

    @Override
    public void savePassed(UUID eventId, UUID applicationId, String details, Instant checkedAt) {
        dsl.insertInto(RESULT)
                .columns(EVENT_ID, APPLICATION_ID, RESULT_VALUE, DETAILS, CHECKED_AT)
                .values(eventId, applicationId, "PASSED", details, checkedAt.atOffset(ZoneOffset.UTC))
                .execute();
    }
}
