package dev.bank.loanplatform.api;

import dev.bank.loanplatform.configuration.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes a safe request identifier before controller execution and returns it to callers.
 * MDC cleanup is guaranteed by try-with-resources, preventing context leakage across servlet threads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final CorrelationIds correlationIds;

    public CorrelationIdFilter(CorrelationIds correlationIds) {
        this.correlationIds = correlationIds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var correlationId = correlationIds.normalizeOrGenerate(request.getHeader(CorrelationIds.HEADER));
        response.setHeader(CorrelationIds.HEADER, correlationId);
        try (var ignored = MDC.putCloseable(CorrelationIds.MDC_KEY, correlationId)) {
            filterChain.doFilter(request, response);
        }
    }
}
