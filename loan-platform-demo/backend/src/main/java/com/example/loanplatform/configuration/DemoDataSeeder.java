package com.example.loanplatform.configuration;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Installs the deterministic local showcase dataset once, regardless of how many API replicas start.
 * The transaction-scoped PostgreSQL lock serializes seeders while stable primary keys and conflict-safe
 * inserts make retries harmless after restarts or partially pre-populated developer databases.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "loan-platform.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final long SEED_LOCK_ID = 4_438_642_265L;
    private static final String SEED_REQUEST_ID = "demo-data-seeder";
    private static final List<SeedApplication> APPLICATIONS = List.of(
            new SeedApplication("11111111-1111-4111-8111-111111111111", "Morava Precision s.r.o.", "18500000", "UNDER_REVIEW", 1, null, 28),
            new SeedApplication("22222222-2222-4222-8222-222222222222", "Bohemia Energo a.s.", "42000000", "APPROVED", 2, null, 18),
            new SeedApplication("33333333-3333-4333-8333-333333333333", "Vltava Logistics s.r.o.", "7800000", "REJECTED", 2, "Nedoložené auditované finanční výkazy za poslední účetní období.", 11),
            new SeedApplication("44444444-4444-4444-8444-444444444444", "Nordwood Interiéry s.r.o.", "12300000", "SUBMITTED", 0, null, 2));

    private final DSLContext dsl;

    public DemoDataSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        dsl.execute("select pg_advisory_xact_lock(?)", SEED_LOCK_ID);
        var inserted = 0;
        for (var seed : APPLICATIONS) {
            inserted += seed(seed);
        }
        log.info("Deterministic demo dataset ensured: configured={}, inserted={}", APPLICATIONS.size(), inserted);
    }

    private int seed(SeedApplication seed) {
        var id = UUID.fromString(seed.id());
        var createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(seed.ageDays());
        var updatedAt = createdAt.plusHours(seed.version() == 0 ? 0 : seed.version());
        var inserted = dsl.execute("""
                insert into loan_application
                    (id, customer_id, amount, currency, status, rejection_reason, version, created_at, updated_at)
                values (?, ?, ?, 'CZK', ?, ?, ?, ?, ?)
                on conflict (id) do nothing
                """, id, seed.companyName(), new BigDecimal(seed.amount()), seed.status(), seed.rejectionReason(),
                seed.version(), Timestamp.from(createdAt.toInstant()), Timestamp.from(updatedAt.toInstant()));
        if (inserted == 0) {
            return 0;
        }

        appendHistory(id, null, "SUBMITTED", 0, createdAt, null, seed.rejectionReason());
        if (seed.version() >= 1) {
            var reviewAt = createdAt.plusHours(1);
            var eventId = stableUuid(seed.id() + "-review");
            dsl.execute("""
                    insert into loan_preprocessing_result (event_id, application_id, result, details, checked_at)
                    values (?, ?, 'PASSED', ?, ?) on conflict (event_id) do nothing
                    """, eventId, id, "Předběžná validační a procesní kontrola byla úspěšně dokončena.", Timestamp.from(reviewAt.toInstant()));
            appendHistory(id, "SUBMITTED", "UNDER_REVIEW", 1, reviewAt, eventId, null);
        }
        if (seed.version() >= 2) {
            appendHistory(id, "UNDER_REVIEW", seed.status(), 2, updatedAt, null, seed.rejectionReason());
        }
        return 1;
    }

    private void appendHistory(UUID applicationId, String previousStatus, String newStatus, long version,
                               OffsetDateTime changedAt, UUID eventId, String reason) {
        dsl.execute("""
                insert into loan_application_status_history
                    (id, application_id, previous_status, new_status, application_version, changed_at,
                     changed_by, request_id, event_id, reason)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do nothing
                """, stableUuid(applicationId + "-history-" + version), applicationId, previousStatus, newStatus,
                version, Timestamp.from(changedAt.toInstant()), version == 1 ? "WORKER" : "API", SEED_REQUEST_ID, eventId, reason);
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record SeedApplication(String id, String companyName, String amount, String status,
                                   long version, String rejectionReason, long ageDays) {
    }
}
