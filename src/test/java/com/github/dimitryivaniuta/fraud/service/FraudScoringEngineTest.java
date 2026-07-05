package com.github.dimitryivaniuta.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.FraudVerdict;
import com.github.dimitryivaniuta.fraud.domain.ScoringResult;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies deterministic and explainable rule scoring behavior.
 */
class FraudScoringEngineTest {

    private final FraudScoringEngine engine = new FraudScoringEngine(new FraudProperties());

    /**
     * High-risk feature signals should push a transaction to manual review or decline.
     */
    @Test
    void scoreReturnsDeclineForStackedHighRiskSignals() {
        TransactionRequest request = request(new BigDecimal("1000.00"), "RU");
        FeatureSnapshot features = new FeatureSnapshot(
            "customer-1", "merchant-1", new BigDecimal("50.00"), new BigDecimal("0.08"),
            true, 2, 18, 4, FeatureSource.REDIS, Instant.now());

        ScoringResult result = engine.score(request, features);

        assertThat(result.verdict()).isEqualTo(FraudVerdict.DECLINE);
        assertThat(result.score()).isBetween(80, 100);
        assertThat(result.reasons()).extracting("code")
            .contains("UNUSUAL_AMOUNT", "HIGH_RISK_COUNTRY", "HIGH_VELOCITY", "NEW_ACCOUNT");
    }

    /**
     * Low-risk transactions should be approved and still include a transparent explanation.
     */
    @Test
    void scoreReturnsApproveForLowRiskSignals() {
        TransactionRequest request = request(new BigDecimal("25.00"), "PL");
        FeatureSnapshot features = new FeatureSnapshot(
            "customer-1", "merchant-1", new BigDecimal("100.00"), BigDecimal.ZERO,
            false, 120, 1, 0, FeatureSource.REDIS, Instant.now());

        ScoringResult result = engine.score(request, features);

        assertThat(result.verdict()).isEqualTo(FraudVerdict.APPROVE);
        assertThat(result.score()).isZero();
        assertThat(result.reasons()).extracting("code").containsExactly("LOW_RISK_PROFILE");
    }

    /**
     * The same input should always produce the same score and reasons.
     */
    @Test
    void scoreIsDeterministicForSameInput() {
        TransactionRequest request = request(new BigDecimal("500.00"), "PL");
        FeatureSnapshot features = new FeatureSnapshot(
            "customer-1", "merchant-1", new BigDecimal("100.00"), new BigDecimal("0.02"),
            false, 365, 10, 1, FeatureSource.REDIS, Instant.now());

        ScoringResult first = engine.score(request, features);
        ScoringResult second = engine.score(request, features);

        assertThat(second).isEqualTo(first);
    }

    private TransactionRequest request(final BigDecimal amount, final String country) {
        return new TransactionRequest(
            "tx-1", "customer-1", "merchant-1", amount, "PLN", country,
            "ECOMMERCE", "device-1", "203.0.113.10", Instant.parse("2026-07-04T10:00:00Z"));
    }
}
