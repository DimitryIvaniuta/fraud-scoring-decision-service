package com.github.dimitryivaniuta.fraud.persistence;

import com.github.dimitryivaniuta.fraud.domain.DecisionReason;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.FraudVerdict;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable fraud decision row mapped from the fraud_decisions table.
 *
 * @param id decision identifier
 * @param transactionId unique transaction identifier
 * @param requestFingerprint canonical request fingerprint
 * @param modelVersion scoring model version used for audit and replay
 * @param verdict persisted decision verdict
 * @param score persisted decision score
 * @param reasons explainability reasons
 * @param request request payload that produced the decision
 * @param featureSnapshot feature snapshot used for scoring
 * @param featureSource origin of the feature snapshot
 * @param correlationId request correlation identifier
 * @param createdAt persistence timestamp
 */
public record DecisionEntity(
    UUID id,
    String transactionId,
    String requestFingerprint,
    String modelVersion,
    FraudVerdict verdict,
    int score,
    List<DecisionReason> reasons,
    TransactionRequest request,
    FeatureSnapshot featureSnapshot,
    FeatureSource featureSource,
    String correlationId,
    Instant createdAt
) {
}
