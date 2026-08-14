package com.example.loanplatform.persistence;

import com.example.loanplatform.application.LoanApplicationRepository;
import com.example.loanplatform.application.OptimisticLockingConflictException;
import com.example.loanplatform.domain.LoanApplication;
import com.example.loanplatform.domain.LoanApplicationStatus;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.jooq.Condition;
import static org.jooq.impl.DSL.noCondition;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/** jOOQ adapter that maps relational rows to the domain aggregate without leaking SQL types upstream. */
@Repository
public class JooqLoanApplicationRepository implements LoanApplicationRepository {

    private static final Table<Record> LOAN_APPLICATION = table(name("loan_application"));
    private static final Field<UUID> ID = field(name("id"), UUID.class);
    private static final Field<String> CUSTOMER_ID = field(name("customer_id"), String.class);
    private static final Field<BigDecimal> AMOUNT = field(name("amount"), BigDecimal.class);
    private static final Field<String> CURRENCY = field(name("currency"), String.class);
    private static final Field<String> STATUS = field(name("status"), String.class);
    private static final Field<String> REJECTION_REASON = field(name("rejection_reason"), String.class);
    private static final Field<Long> VERSION = field(name("version"), Long.class);
    private static final Field<OffsetDateTime> CREATED_AT = field(name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field(name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqLoanApplicationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(LoanApplication application) {
        dsl.insertInto(LOAN_APPLICATION)
                .columns(ID, CUSTOMER_ID, AMOUNT, CURRENCY, STATUS, REJECTION_REASON, VERSION, CREATED_AT, UPDATED_AT)
                .values(
                        application.getId(),
                        application.getCustomerId(),
                        application.getAmount(),
                        application.getCurrency().getCurrencyCode(),
                        application.getStatus().name(),
                        application.getRejectionReason(),
                        application.getVersion(),
                        atUtc(application.getCreatedAt()),
                        atUtc(application.getUpdatedAt()))
                .execute();
    }

    @Override
    public void update(LoanApplication application, long expectedVersion) {
        // Version in the predicate turns this into a database-level compare-and-set operation.
        var affectedRows = dsl.update(LOAN_APPLICATION)
                .set(STATUS, application.getStatus().name())
                .set(REJECTION_REASON, application.getRejectionReason())
                .set(VERSION, application.getVersion())
                .set(UPDATED_AT, atUtc(application.getUpdatedAt()))
                .where(ID.eq(application.getId()))
                .and(VERSION.eq(expectedVersion))
                .execute();

        if (affectedRows != 1) {
            throw new OptimisticLockingConflictException(application.getId(), expectedVersion);
        }
    }

    @Override
    public Optional<LoanApplication> findById(UUID id) {
        return dsl.select(ID, CUSTOMER_ID, AMOUNT, CURRENCY, STATUS, REJECTION_REASON, VERSION, CREATED_AT, UPDATED_AT)
                .from(LOAN_APPLICATION)
                .where(ID.eq(id))
                .fetchOptional(this::toDomain);
    }

    @Override
    public List<LoanApplication> findAll(
            LoanApplicationStatus requestedStatus,
            String query,
            int offset,
            int limit) {
        return dsl.select(ID, CUSTOMER_ID, AMOUNT, CURRENCY, STATUS, REJECTION_REASON, VERSION, CREATED_AT, UPDATED_AT)
                .from(LOAN_APPLICATION)
                .where(filters(requestedStatus, query))
                .orderBy(UPDATED_AT.desc(), ID.desc())
                .offset(offset)
                .limit(limit)
                .fetch(this::toDomain);
    }

    @Override
    public long count(LoanApplicationStatus requestedStatus, String query) {
        return dsl.fetchCount(LOAN_APPLICATION, filters(requestedStatus, query));
    }

    private static Condition filters(LoanApplicationStatus requestedStatus, String query) {
        Condition condition = noCondition();
        if (requestedStatus != null) {
            condition = condition.and(STATUS.eq(requestedStatus.name()));
        }
        if (query != null) {
            var pattern = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
            condition = condition.and(CUSTOMER_ID.containsIgnoreCase(query)
                    .or(ID.cast(String.class).likeIgnoreCase(pattern, '\\')));
        }
        return condition;
    }

    private LoanApplication toDomain(Record record) {
        return LoanApplication.restore(
                record.get(ID),
                record.get(CUSTOMER_ID),
                record.get(AMOUNT),
                Currency.getInstance(record.get(CURRENCY)),
                LoanApplicationStatus.valueOf(record.get(STATUS)),
                record.get(VERSION),
                toInstant(record.get(CREATED_AT)),
                toInstant(record.get(UPDATED_AT)),
                record.get(REJECTION_REASON));
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime.toInstant();
    }
}
