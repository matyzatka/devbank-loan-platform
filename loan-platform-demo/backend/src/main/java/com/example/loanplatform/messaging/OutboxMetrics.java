package com.example.loanplatform.messaging;

import com.example.loanplatform.application.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Exposes backlog size and age, the primary health signals for an asynchronous outbox. */
@Component
public class OutboxMetrics implements MeterBinder {

    private final OutboxRepository outbox;
    private final Clock clock;

    public OutboxMetrics(OutboxRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("loan.outbox.pending", outbox, OutboxRepository::countUnpublished)
                .description("Number of unpublished transactional outbox events")
                .register(registry);
        Gauge.builder("loan.outbox.oldest.age", outbox, repository -> oldestAgeSeconds())
                .description("Age in seconds of the oldest unpublished outbox event")
                .baseUnit("seconds")
                .register(registry);
    }

    private double oldestAgeSeconds() {
        var oldest = outbox.oldestUnpublishedAt();
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest, clock.instant()).toSeconds());
    }
}
