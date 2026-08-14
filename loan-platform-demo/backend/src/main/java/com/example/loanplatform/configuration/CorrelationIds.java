package com.example.loanplatform.configuration;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    public String normalizeOrGenerate(String candidate) {
        return candidate != null && SAFE_VALUE.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    public String currentOrGenerate() {
        return Optional.ofNullable(MDC.get(MDC_KEY)).orElseGet(() -> UUID.randomUUID().toString());
    }
}
