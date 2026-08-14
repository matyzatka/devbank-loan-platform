package dev.bank.loanplatform.configuration;

import dev.bank.loanplatform.LoanPlatformApplication;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;

@Testcontainers
@SpringBootTest(
        classes = LoanPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "loan-platform.demo-data.enabled=true",
                "loan-platform.kafka.enabled=false",
                "loan-platform.outbox.publisher-enabled=false"
        })
class DemoDataSeederTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private DSLContext dsl;

    @Test
    void concurrentSeederRunsKeepOneDeterministicDataset() {
        clearSeedTables();
        var arguments = new DefaultApplicationArguments();

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> seeder.run(arguments)),
                CompletableFuture.runAsync(() -> seeder.run(arguments)),
                CompletableFuture.runAsync(() -> seeder.run(arguments)))
                .join();

        assertThat(dsl.fetchCount(table("loan_application"))).isEqualTo(4);
        assertThat(dsl.fetchCount(table("loan_application_status_history"))).isEqualTo(9);
        assertThat(dsl.fetchCount(table("loan_preprocessing_result"))).isEqualTo(3);
    }

    private void clearSeedTables() {
        dsl.deleteFrom(table("loan_application_status_history")).execute();
        dsl.deleteFrom(table("loan_preprocessing_result")).execute();
        dsl.deleteFrom(table("outbox_event")).execute();
        dsl.deleteFrom(table("idempotency_record")).execute();
        dsl.deleteFrom(table("loan_application")).execute();
    }
}
