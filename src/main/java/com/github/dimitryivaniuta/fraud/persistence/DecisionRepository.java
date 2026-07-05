package com.github.dimitryivaniuta.fraud.persistence;

import com.github.dimitryivaniuta.fraud.domain.DecisionReason;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.FraudVerdict;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import com.github.dimitryivaniuta.fraud.service.JsonService;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive persistence gateway for fraud decisions.
 */
@Repository
@RequiredArgsConstructor
public class DecisionRepository {

    private static final String SELECT_COLUMNS = """
        SELECT id, transaction_id, request_fingerprint, model_version, verdict, score,
               reasons::text AS reasons, request_payload::text AS request_payload,
               feature_snapshot::text AS feature_snapshot, feature_source, correlation_id, created_at
          FROM fraud_decisions
        """;

    private final DatabaseClient databaseClient;
    private final JsonService jsonService;

    /**
     * Finds a persisted decision by transaction id.
     *
     * @param transactionId transaction identifier
     * @return matching decision, if it exists
     */
    public Mono<DecisionEntity> findByTransactionId(final String transactionId) {
        return databaseClient.sql(SELECT_COLUMNS + " WHERE transaction_id = :transactionId")
            .bind("transactionId", transactionId)
            .map((row, metadata) -> map(row))
            .one();
    }

    /**
     * Inserts a fraud decision after the score has been calculated.
     *
     * @param decision decision entity to persist
     * @return inserted decision entity
     */
    public Mono<DecisionEntity> insert(final DecisionEntity decision) {
        return databaseClient.sql("""
            INSERT INTO fraud_decisions (
                id, transaction_id, request_fingerprint, model_version, verdict, score, reasons,
                request_payload, feature_snapshot, feature_source, correlation_id, created_at
            ) VALUES (
                :id, :transactionId, :requestFingerprint, :modelVersion, :verdict, :score, CAST(:reasons AS JSONB),
                CAST(:requestPayload AS JSONB), CAST(:featureSnapshot AS JSONB), :featureSource, :correlationId, :createdAt
            )
            """)
            .bind("id", decision.id())
            .bind("transactionId", decision.transactionId())
            .bind("requestFingerprint", decision.requestFingerprint())
            .bind("modelVersion", decision.modelVersion())
            .bind("verdict", decision.verdict().name())
            .bind("score", decision.score())
            .bind("reasons", jsonService.write(decision.reasons()))
            .bind("requestPayload", jsonService.write(decision.request()))
            .bind("featureSnapshot", jsonService.write(decision.featureSnapshot()))
            .bind("featureSource", decision.featureSource().name())
            .bind("correlationId", decision.correlationId())
            .bind("createdAt", decision.createdAt())
            .fetch()
            .rowsUpdated()
            .thenReturn(decision);
    }

    private DecisionEntity map(final Row row) {
        return new DecisionEntity(
            row.get("id", UUID.class),
            row.get("transaction_id", String.class),
            row.get("request_fingerprint", String.class),
            row.get("model_version", String.class),
            FraudVerdict.valueOf(row.get("verdict", String.class)),
            row.get("score", Integer.class),
            readReasons(row.get("reasons", String.class)),
            jsonService.read(row.get("request_payload", String.class), TransactionRequest.class),
            jsonService.read(row.get("feature_snapshot", String.class), FeatureSnapshot.class),
            FeatureSource.valueOf(row.get("feature_source", String.class)),
            row.get("correlation_id", String.class),
            row.get("created_at", Instant.class)
        );
    }

    private List<DecisionReason> readReasons(final String json) {
        DecisionReason[] reasons = jsonService.read(json, DecisionReason[].class);
        return Arrays.asList(reasons);
    }
}
