package com.example.loanplatform.persistence;

import com.example.loanplatform.application.LoanApplicationRepository;
import com.example.loanplatform.application.OptimisticLockingConflictException;
import com.example.loanplatform.LoanPlatformApplication;
import com.example.loanplatform.domain.LoanApplication;
import com.example.loanplatform.domain.LoanApplicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
        classes = LoanPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JooqLoanApplicationRepositoryTest {

    private static final Clock SUBMITTED_AT = fixedClock("2026-08-14T10:00:00Z");
    private static final Clock REVIEWED_AT = fixedClock("2026-08-14T11:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private LoanApplicationRepository repository;

    @Test
    void insertsAndRetrievesApplication() {
        var application = newApplication();

        repository.insert(application);

        var restored = repository.findById(application.getId()).orElseThrow();
        assertThat(restored.getId()).isEqualTo(application.getId());
        assertThat(restored.getCustomerId()).isEqualTo(application.getCustomerId());
        assertThat(restored.getAmount()).isEqualByComparingTo(application.getAmount());
        assertThat(restored.getCurrency()).isEqualTo(application.getCurrency());
        assertThat(restored.getStatus()).isEqualTo(LoanApplicationStatus.SUBMITTED);
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getCreatedAt()).isEqualTo(SUBMITTED_AT.instant());
    }

    @Test
    void updatesApplicationWhenVersionMatches() {
        var application = newApplication();
        repository.insert(application);
        application.startReview(REVIEWED_AT);

        repository.update(application, 0);

        var restored = repository.findById(application.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_REVIEW);
        assertThat(restored.getVersion()).isOne();
        assertThat(restored.getUpdatedAt()).isEqualTo(REVIEWED_AT.instant());
    }

    @Test
    void reportsOptimisticLockingConflictForStaleVersion() {
        var application = newApplication();
        repository.insert(application);
        application.startReview(REVIEWED_AT);
        repository.update(application, 0);
        application.approve(fixedClock("2026-08-14T12:00:00Z"));

        assertThatThrownBy(() -> repository.update(application, 0))
                .isInstanceOf(OptimisticLockingConflictException.class)
                .hasMessageContaining(application.getId().toString())
                .hasMessageContaining("version 0");
    }

    @Test
    void returnsEmptyForUnknownApplication() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    private static LoanApplication newApplication() {
        return LoanApplication.submit(
                UUID.randomUUID(),
                "CORP-123",
                new BigDecimal("2500000.00"),
                Currency.getInstance("EUR"),
                SUBMITTED_AT);
    }

    private static Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
