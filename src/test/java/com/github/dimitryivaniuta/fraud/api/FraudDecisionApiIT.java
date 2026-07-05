package com.github.dimitryivaniuta.fraud.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.fraud.domain.DecisionResponse;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for the WebFlux decision API, R2DBC persistence, Flyway, and Redis cache path.
 */
@Testcontainers
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FraudDecisionApiIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4-alpine"))
        .withDatabaseName("frauddb")
        .withUsername("fraud")
        .withPassword("fraud");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine"))
        .withExposedPorts(6379);

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Supplies container connection settings to the Spring context.
     *
     * @param registry dynamic property registry
     */
    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ':'
            + POSTGRES.getMappedPort(5432) + '/' + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("fraud.kafka.enabled", () -> "false");
    }

    /**
     * A decision should be persisted and returned consistently under exact retries.
     */
    @Test
    void decisionIsPersistedAndRetryReturnsSameDecision() {
        seedFeature("customer-it-1", "merchant-it-1");
        TransactionRequest request = request("it-tx-1", "customer-it-1", "merchant-it-1", new BigDecimal("500.00"));

        DecisionResponse first = webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .header("X-Correlation-Id", "it-correlation-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(DecisionResponse.class)
            .returnResult()
            .getResponseBody();

        DecisionResponse retry = webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .header("X-Correlation-Id", "it-correlation-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(DecisionResponse.class)
            .returnResult()
            .getResponseBody();

        assertThat(first).isNotNull();
        assertThat(retry).isNotNull();
        assertThat(retry.decisionId()).isEqualTo(first.decisionId());
        assertThat(retry.transactionId()).isEqualTo(first.transactionId());
        assertThat(retry.idempotentReplay()).isTrue();
        assertThat(retry.modelVersion()).isNotBlank();

        webTestClient.get()
            .uri("/api/v1/fraud/decisions/{transactionId}", request.transactionId())
            .exchange()
            .expectStatus().isOk()
            .expectBody(DecisionResponse.class)
            .value(response -> assertThat(response.decisionId()).isEqualTo(first.decisionId()));
    }


    /**
     * Concurrent identical retries should converge to one stored decision without creating duplicate rows.
     */
    @Test
    void concurrentSamePayloadRetriesReturnSameDecision() {
        TransactionRequest request = request("it-tx-concurrent", "customer-it-concurrent",
            "merchant-it-concurrent", new BigDecimal("60.00"));

        CompletableFuture<DecisionResponse> first = CompletableFuture.supplyAsync(() -> postDecision(request));
        CompletableFuture<DecisionResponse> second = CompletableFuture.supplyAsync(() -> postDecision(request));

        DecisionResponse firstResponse = first.join();
        DecisionResponse secondResponse = second.join();

        assertThat(firstResponse.decisionId()).isEqualTo(secondResponse.decisionId());
        assertThat(firstResponse.transactionId()).isEqualTo(secondResponse.transactionId());
    }

    /**
     * Reusing a transaction id with different content should be rejected to preserve consistency.
     */
    @Test
    void retryWithDifferentPayloadReturnsConflict() {
        TransactionRequest first = request("it-tx-2", "customer-it-2", "merchant-it-2", new BigDecimal("40.00"));
        TransactionRequest conflicting = request("it-tx-2", "customer-it-2", "merchant-it-2", new BigDecimal("41.00"));

        webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .bodyValue(first)
            .exchange()
            .expectStatus().isCreated();

        webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .bodyValue(conflicting)
            .exchange()
            .expectStatus().isEqualTo(409);
    }


    /**
     * Invalid ISO-like request fields should be rejected before scoring.
     */
    @Test
    void invalidRequestReturnsBadRequest() {
        TransactionRequest request = new TransactionRequest("it-tx-invalid", "customer-it-invalid",
            "merchant-it-invalid", new BigDecimal("10.00"), "pln", "POL", "ECOMMERCE",
            "device-it", "203.0.113.15", Instant.parse("2026-07-04T10:00:00Z"));

        webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    /**
     * Missing feature data should use a safe fallback and still persist a decision.
     */
    @Test
    void missingFeaturesUseFallbackAndStillRecordDecision() {
        TransactionRequest request = request("it-tx-3", "unknown-customer", "unknown-merchant", new BigDecimal("20.00"));

        webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(DecisionResponse.class)
            .value(response -> assertThat(response.featureSource()).isEqualTo(FeatureSource.FALLBACK));
    }

    private DecisionResponse postDecision(final TransactionRequest request) {
        return webTestClient.post()
            .uri("/api/v1/fraud/decisions")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(DecisionResponse.class)
            .returnResult()
            .getResponseBody();
    }

    private void seedFeature(final String customerId, final String merchantId) {
        FeatureSnapshot snapshot = new FeatureSnapshot(customerId, merchantId, new BigDecimal("80.00"),
            new BigDecimal("0.01"), false, 300, 3, 0, FeatureSource.REDIS, Instant.now());
        webTestClient.put()
            .uri("/api/v1/fraud/features")
            .bodyValue(snapshot)
            .exchange()
            .expectStatus().isOk();
    }

    private TransactionRequest request(
        final String transactionId,
        final String customerId,
        final String merchantId,
        final BigDecimal amount
    ) {
        return new TransactionRequest(transactionId, customerId, merchantId, amount, "PLN", "PL",
            "ECOMMERCE", "device-it", "203.0.113.15", Instant.parse("2026-07-04T10:00:00Z"));
    }
}
