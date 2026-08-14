package dev.bank.loanplatform.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanApplicationTest {

    private static final UUID APPLICATION_ID = UUID.fromString("f6064962-b95d-491f-beba-c01a927fd853");
    private static final Clock SUBMITTED_AT = fixedClock("2026-08-14T10:00:00Z");
    private static final Clock CHANGED_AT = fixedClock("2026-08-14T11:00:00Z");

    @Test
    void submitsNewApplication() {
        var application = submittedApplication();

        assertThat(application.getId()).isEqualTo(APPLICATION_ID);
        assertThat(application.getCustomerId()).isEqualTo("CORP-123");
        assertThat(application.getAmount()).isEqualByComparingTo("2500000.00");
        assertThat(application.getCurrency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.SUBMITTED);
        assertThat(application.getVersion()).isZero();
        assertThat(application.getCreatedAt()).isEqualTo(SUBMITTED_AT.instant());
        assertThat(application.getUpdatedAt()).isEqualTo(SUBMITTED_AT.instant());
    }

    @Test
    void movesFromSubmittedToUnderReview() {
        var application = submittedApplication();

        application.startReview(CHANGED_AT);

        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_REVIEW);
        assertThat(application.getVersion()).isOne();
        assertThat(application.getUpdatedAt()).isEqualTo(CHANGED_AT.instant());
    }

    @ParameterizedTest
    @MethodSource("reviewDecisions")
    void completesApplicationFromReview(
            Consumer<LoanApplication> decision,
            LoanApplicationStatus expectedStatus) {
        var application = submittedApplication();
        application.startReview(CHANGED_AT);

        decision.accept(application);

        assertThat(application.getStatus()).isEqualTo(expectedStatus);
        assertThat(application.getVersion()).isEqualTo(2);
    }

    @ParameterizedTest(name = "cannot execute {1} while status is {0}")
    @MethodSource("invalidTransitions")
    void rejectsInvalidTransitions(
            LoanApplicationStatus initialStatus,
            LoanApplicationStatus targetStatus,
            Consumer<LoanApplication> transition) {
        var application = applicationIn(initialStatus);
        var versionBeforeAttempt = application.getVersion();

        assertThatThrownBy(() -> transition.accept(application))
                .isInstanceOf(InvalidLoanApplicationTransitionException.class)
                .hasMessageContaining(initialStatus.name())
                .hasMessageContaining(targetStatus.name());

        assertThat(application.getStatus()).isEqualTo(initialStatus);
        assertThat(application.getVersion()).isEqualTo(versionBeforeAttempt);
    }

    @ParameterizedTest
    @MethodSource("invalidSubmissionData")
    void rejectsInvalidSubmissionData(
            String customerId,
            BigDecimal amount,
            Currency currency,
            String expectedMessage) {
        assertThatThrownBy(() -> LoanApplication.submit(
                APPLICATION_ID, customerId, amount, currency, SUBMITTED_AT))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessage(expectedMessage);
    }

    private static Stream<Arguments> reviewDecisions() {
        return Stream.of(
                Arguments.of((Consumer<LoanApplication>) application -> application.approve(CHANGED_AT),
                        LoanApplicationStatus.APPROVED),
                Arguments.of((Consumer<LoanApplication>) application -> application.reject("Nedoložené podklady", CHANGED_AT),
                        LoanApplicationStatus.REJECTED));
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(LoanApplicationStatus.SUBMITTED, LoanApplicationStatus.APPROVED,
                        (Consumer<LoanApplication>) application -> application.approve(CHANGED_AT)),
                Arguments.of(LoanApplicationStatus.SUBMITTED, LoanApplicationStatus.REJECTED,
                        (Consumer<LoanApplication>) application -> application.reject("Nedoložené podklady", CHANGED_AT)),
                Arguments.of(LoanApplicationStatus.UNDER_REVIEW, LoanApplicationStatus.UNDER_REVIEW,
                        (Consumer<LoanApplication>) application -> application.startReview(CHANGED_AT)),
                Arguments.of(LoanApplicationStatus.APPROVED, LoanApplicationStatus.REJECTED,
                        (Consumer<LoanApplication>) application -> application.reject("Nedoložené podklady", CHANGED_AT)),
                Arguments.of(LoanApplicationStatus.REJECTED, LoanApplicationStatus.APPROVED,
                        (Consumer<LoanApplication>) application -> application.approve(CHANGED_AT)));
    }

    private static Stream<Arguments> invalidSubmissionData() {
        return Stream.of(
                Arguments.of(" ", new BigDecimal("100.00"), Currency.getInstance("EUR"),
                        "customerId must not be blank"),
                Arguments.of("CORP-123", BigDecimal.ZERO, Currency.getInstance("EUR"),
                        "amount must be greater than zero"),
                Arguments.of("CORP-123", new BigDecimal("100.00"), null,
                        "currency must not be null"));
    }

    private static LoanApplication applicationIn(LoanApplicationStatus status) {
        var application = submittedApplication();
        if (status != LoanApplicationStatus.SUBMITTED) {
            application.startReview(CHANGED_AT);
        }
        if (status == LoanApplicationStatus.APPROVED) {
            application.approve(CHANGED_AT);
        } else if (status == LoanApplicationStatus.REJECTED) {
            application.reject("Nedoložené podklady", CHANGED_AT);
        }
        return application;
    }

    private static LoanApplication submittedApplication() {
        return LoanApplication.submit(
                APPLICATION_ID,
                "CORP-123",
                new BigDecimal("2500000.00"),
                Currency.getInstance("EUR"),
                SUBMITTED_AT);
    }

    private static Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
