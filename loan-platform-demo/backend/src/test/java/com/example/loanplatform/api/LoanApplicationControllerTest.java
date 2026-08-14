package com.example.loanplatform.api;

import com.example.loanplatform.LoanPlatformApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.loanplatform.application.LoanApplicationService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.field;

@Testcontainers
@SpringBootTest(
        classes = LoanPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "loan-platform.kafka.enabled=false",
                "loan-platform.outbox.publisher-enabled=false"
        })
class LoanApplicationControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private LoanApplicationService service;

    @BeforeEach
    void clearDatabase() {
        dsl.deleteFrom(table("loan_application_status_history")).execute();
        dsl.deleteFrom(table("loan_preprocessing_result")).execute();
        dsl.deleteFrom(table("outbox_event")).execute();
        dsl.deleteFrom(table("idempotency_record")).execute();
        dsl.deleteFrom(table("loan_application")).execute();
    }

    @Test
    void createsRetrievesAndIdempotentlyRepeatsApplication() throws Exception {
        var first = create("api-create-001", validRequest());
        var repeated = create("api-create-001", validRequest());

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("Location")).isPresent();
        assertThat(first.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/json");
        var firstBody = json(first);
        var repeatedBody = json(repeated);
        assertThat(repeated.statusCode()).isEqualTo(201);
        assertThat(repeatedBody.get("id").asText()).isEqualTo(firstBody.get("id").asText());
        assertThat(repeatedBody.get("status").asText()).isEqualTo("SUBMITTED");

        var retrieved = get("/api/v1/applications/" + firstBody.get("id").asText());
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(json(retrieved).get("customerId").asText()).isEqualTo("CORP-123");
        assertThat(dsl.fetchCount(table("loan_application"))).isOne();
        assertThat(dsl.fetchCount(table("outbox_event"))).isOne();
    }

    @Test
    void propagatesCorrelationIdToResponseProblemAndOutbox() throws Exception {
        var correlationId = "portfolio-demo-42";
        var response = create("api-correlation-001", validRequest(), correlationId);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("X-Correlation-ID")).contains(correlationId);
        assertThat(dsl.select(field("request_id", String.class))
                .from(table("outbox_event"))
                .fetchSingle(field("request_id", String.class)))
                .isEqualTo(correlationId);

        var problem = HttpRequest.newBuilder(uri("/api/v1/applications/" + UUID.randomUUID()))
                .header("X-Correlation-ID", correlationId)
                .GET()
                .build();
        var problemResponse = http.send(problem, HttpResponse.BodyHandlers.ofString());
        assertThat(json(problemResponse).get("requestId").asText()).isEqualTo(correlationId);
    }

    @Test
    void exposesOutboxBacklogMetric() throws Exception {
        create("api-metric-001", validRequest());

        var response = get("/actuator/metrics/loan.outbox.pending");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).at("/measurements/0/value").asDouble()).isEqualTo(1);
    }

    @Test
    void listsAndFiltersApplications() throws Exception {
        create("api-list-001", validRequest());
        create("api-list-002", """
                {"customerId":"ACME-987","amount":750000.00,"currency":"USD"}
                """);

        var all = json(get("/api/v1/applications?size=10"));
        var filtered = json(get("/api/v1/applications?query=ACME&status=SUBMITTED"));

        assertThat(all.get("totalElements").asInt()).isEqualTo(2);
        assertThat(all.get("items").size()).isEqualTo(2);
        assertThat(filtered.get("totalElements").asInt()).isOne();
        assertThat(filtered.at("/items/0/customerId").asText()).isEqualTo("ACME-987");
    }

    @Test
    void returnsValidationProblemForInvalidRequest() throws Exception {
        var response = create(
                "api-invalid-001",
                """
                {"customerId":"","amount":0,"currency":"eur"}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertProblem(response, "VALIDATION_FAILED");
        assertThat(json(response).get("violations").size()).isEqualTo(3);
        assertThat(dsl.fetchCount(table("loan_application"))).isZero();
    }

    @Test
    void returnsBadRequestWhenIdempotencyHeaderIsMissing() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/applications"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validRequest()))
                .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
    }

    @Test
    void rejectsIdempotencyKeyUsedForDifferentPayload() throws Exception {
        create("api-reused-key", validRequest());

        var response = create(
                "api-reused-key",
                """
                {"customerId":"CORP-123","amount":2600000.00,"currency":"EUR"}
                """);

        assertThat(response.statusCode()).isEqualTo(409);
        assertProblem(response, "BUSINESS_CONFLICT");
    }

    @Test
    void movesApplicationThroughReviewAndApproval() throws Exception {
        var created = json(create("api-workflow-001", validRequest()));
        var id = created.get("id").asText();

        service.startReviewFromWorker(
                UUID.fromString(id),
                "worker-test-request",
                UUID.randomUUID());
        var approved = postAction("/api/v1/applications/" + id + "/approve");
        var invalid = postAction("/api/v1/applications/" + id + "/reject");

        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(json(approved).get("status").asText()).isEqualTo("APPROVED");
        assertThat(invalid.statusCode()).isEqualTo(409);
        assertProblem(invalid, "BUSINESS_CONFLICT");
        assertThat(dsl.fetchCount(table("outbox_event"))).isEqualTo(3);
        assertThat(dsl.fetchCount(table("loan_application_status_history"))).isEqualTo(3);
    }

    @Test
    void returnsNotFoundProblemForUnknownApplication() throws Exception {
        var response = get("/api/v1/applications/" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertProblem(response, "APPLICATION_NOT_FOUND");
    }

    @Test
    void exposesGeneratedOpenApiDocument() throws Exception {
        var response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        var document = json(response);
        assertThat(document.at("/info/title").asText()).isEqualTo("Loan Platform API");
        assertThat(document.at("/paths/~1api~1v1~1applications/post").isObject()).isTrue();
    }

    private HttpResponse<String> create(String idempotencyKey, String body) throws Exception {
        return create(idempotencyKey, body, null);
    }

    private HttpResponse<String> create(String idempotencyKey, String body, String correlationId) throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/applications"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (correlationId != null) {
            request.header("X-Correlation-ID", correlationId);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postAction(String path) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private void assertProblem(HttpResponse<String> response, String expectedCode) throws Exception {
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        assertThat(json(response).get("code").asText()).isEqualTo(expectedCode);
    }

    private static String validRequest() {
        return """
                {"customerId":"CORP-123","amount":2500000.00,"currency":"EUR"}
                """;
    }
}
