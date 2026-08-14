package dev.bank.loanplatform.persistence;

import dev.bank.loanplatform.application.IdempotencyClaim;
import dev.bank.loanplatform.application.IdempotencyRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
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

/** PostgreSQL-backed idempotency adapter using insert-on-conflict as an atomic claim primitive. */
@Repository
public class JooqIdempotencyRepository implements IdempotencyRepository {

    private static final Table<Record> IDEMPOTENCY = table(name("idempotency_record"));
    private static final Field<String> KEY = field(name("idempotency_key"), String.class);
    private static final Field<String> REQUEST_HASH = field(name("request_hash"), String.class);
    private static final Field<UUID> APPLICATION_ID = field(name("application_id"), UUID.class);
    private static final Field<OffsetDateTime> CREATED_AT = field(name("created_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqIdempotencyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public IdempotencyClaim claim(
            String idempotencyKey,
            String requestHash,
            UUID proposedApplicationId,
            Instant createdAt) {
        // ON CONFLICT avoids a check-then-insert race between concurrent retries.
        var inserted = dsl.insertInto(IDEMPOTENCY)
                .columns(KEY, REQUEST_HASH, APPLICATION_ID, CREATED_AT)
                .values(
                        idempotencyKey,
                        requestHash,
                        proposedApplicationId,
                        createdAt.atOffset(ZoneOffset.UTC))
                .onConflict(KEY)
                .doNothing()
                .execute();

        if (inserted == 1) {
            return new IdempotencyClaim(proposedApplicationId, requestHash, true);
        }

        return dsl.select(APPLICATION_ID, REQUEST_HASH)
                .from(IDEMPOTENCY)
                .where(KEY.eq(idempotencyKey))
                .fetchOptional(record -> new IdempotencyClaim(
                        record.get(APPLICATION_ID),
                        record.get(REQUEST_HASH),
                        false))
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency claim disappeared after a key conflict"));
    }
}
