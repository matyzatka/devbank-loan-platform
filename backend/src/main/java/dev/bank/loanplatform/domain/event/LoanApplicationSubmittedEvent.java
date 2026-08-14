package dev.bank.loanplatform.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Signals that committed application state is ready for preliminary asynchronous processing. */
public record LoanApplicationSubmittedEvent(
        UUID eventId,
        UUID applicationId,
        String customerId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) implements LoanApplicationEvent {

    @Override
    public String eventType() {
        return "LoanApplicationSubmitted";
    }
}
