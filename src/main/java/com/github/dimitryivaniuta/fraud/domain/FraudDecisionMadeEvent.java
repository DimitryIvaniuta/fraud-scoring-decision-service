package com.github.dimitryivaniuta.fraud.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event published after a decision has been durably stored.
 *
 * @param eventId unique event identifier
 * @param decisionId persisted decision identifier
 * @param transactionId transaction identifier
 * @param customerId customer identifier
 * @param merchantId merchant identifier
 * @param modelVersion scoring model version used to make the decision
 * @param verdict final fraud verdict
 * @param score numeric fraud score
 * @param reasons explainable decision reasons
 * @param featureSource source of the feature snapshot
 * @param correlationId request correlation identifier
 * @param occurredAt event creation timestamp
 */
public record FraudDecisionMadeEvent(
    UUID eventId,
    UUID decisionId,
    String transactionId,
    String customerId,
    String merchantId,
    String modelVersion,
    FraudVerdict verdict,
    int score,
    List<DecisionReason> reasons,
    FeatureSource featureSource,
    String correlationId,
    Instant occurredAt
) {
}
