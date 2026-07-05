package com.github.dimitryivaniuta.fraud.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API response returned after the service has persisted a fraud decision.
 *
 * @param decisionId persisted decision identifier
 * @param transactionId transaction identifier
 * @param modelVersion scoring model version used to make the decision
 * @param verdict final fraud verdict
 * @param score numeric fraud score from 0 to 100
 * @param reasons explainable decision reasons
 * @param featureSource origin of the feature data used for scoring
 * @param cacheHit whether feature data came from Redis
 * @param idempotentReplay whether the response is a replay of an already persisted decision
 * @param decisionTimeMs wall-clock time spent in the synchronous decision path
 * @param correlationId request correlation identifier
 * @param createdAt persisted decision timestamp
 */
public record DecisionResponse(
    UUID decisionId,
    String transactionId,
    String modelVersion,
    FraudVerdict verdict,
    int score,
    List<DecisionReason> reasons,
    FeatureSource featureSource,
    boolean cacheHit,
    boolean idempotentReplay,
    long decisionTimeMs,
    String correlationId,
    Instant createdAt
) {
}
