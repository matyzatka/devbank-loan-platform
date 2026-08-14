package dev.bank.loanplatform.configuration;

import dev.bank.loanplatform.application.RequestIdProvider;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Normalizes untrusted correlation headers and exposes the active request identifier to use cases. */
@Component
public class CorrelationIds implements RequestIdProvider {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    public String normalizeOrGenerate(String candidate) {
        // Restrict values before placing them in logs or transport headers to prevent log injection.
        return candidate != null && SAFE_VALUE.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    @Override
    public String currentOrGenerate() {
        return Optional.ofNullable(MDC.get(MDC_KEY)).orElseGet(() -> UUID.randomUUID().toString());
    }
}
