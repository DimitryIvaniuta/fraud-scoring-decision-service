package com.github.dimitryivaniuta.fraud.service;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.domain.DecisionReason;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FraudVerdict;
import com.github.dimitryivaniuta.fraud.domain.ScoringResult;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Deterministic, explainable, rule-based scoring engine optimized for the synchronous path.
 */
@Component
@RequiredArgsConstructor
public class FraudScoringEngine {

    private final FraudProperties properties;

    /**
     * Scores a transaction using the provided feature snapshot.
     *
     * @param request transaction request
     * @param features feature snapshot
     * @return scoring result containing verdict, score, and reasons
     */
    public ScoringResult score(final TransactionRequest request, final FeatureSnapshot features) {
        List<DecisionReason> reasons = new ArrayList<>();
        int score = 0;

        score += unusualAmountContribution(request, features, reasons);
        score += countryContribution(request, features, reasons);
        score += velocityContribution(features, reasons);
        score += chargebackContribution(features, reasons);
        score += priorDeclineContribution(features, reasons);
        score += accountAgeContribution(features, reasons);

        int boundedScore = Math.max(0, Math.min(100, score));
        FraudVerdict verdict = verdictFor(boundedScore);
        if (reasons.isEmpty()) {
            reasons.add(new DecisionReason("LOW_RISK_PROFILE", "No active fraud rule contributed to the score", 0));
        }
        return new ScoringResult(properties.getScoring().getModelVersion(), verdict, boundedScore, List.copyOf(reasons));
    }

    private int unusualAmountContribution(
        final TransactionRequest request,
        final FeatureSnapshot features,
        final List<DecisionReason> reasons
    ) {
        BigDecimal multiplier = BigDecimal.valueOf(properties.getScoring().getUnusualAmountMultiplier());
        BigDecimal expectedCeiling = features.averageAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        if (request.amount().compareTo(expectedCeiling) > 0) {
            int contribution = properties.getScoring().getUnusualAmountWeight();
            reasons.add(new DecisionReason("UNUSUAL_AMOUNT",
                "Amount is above " + multiplier + "x historical average for this customer and merchant",
                contribution));
            return contribution;
        }
        return 0;
    }

    private int countryContribution(
        final TransactionRequest request,
        final FeatureSnapshot features,
        final List<DecisionReason> reasons
    ) {
        boolean highRiskByRequest = properties.getScoring().getHighRiskCountries().stream()
            .anyMatch(country -> country.equalsIgnoreCase(request.country()));
        if (highRiskByRequest || features.highRiskCountry()) {
            int contribution = properties.getScoring().getHighRiskCountryWeight();
            reasons.add(new DecisionReason("HIGH_RISK_COUNTRY",
                "Transaction country is configured as high risk", contribution));
            return contribution;
        }
        return 0;
    }

    private int velocityContribution(final FeatureSnapshot features, final List<DecisionReason> reasons) {
        if (features.velocity24h() > properties.getScoring().getVelocityThreshold24h()) {
            int overflow = features.velocity24h() - properties.getScoring().getVelocityThreshold24h();
            int contribution = Math.min(properties.getScoring().getVelocityWeight() + overflow, 35);
            reasons.add(new DecisionReason("HIGH_VELOCITY",
                "Customer velocity in the last 24 hours exceeds the safe threshold", contribution));
            return contribution;
        }
        return 0;
    }

    private int chargebackContribution(final FeatureSnapshot features, final List<DecisionReason> reasons) {
        int contribution = features.chargebackRate()
            .multiply(BigDecimal.valueOf(properties.getScoring().getChargebackWeight() * 10L))
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
        contribution = Math.max(0, Math.min(25, contribution));
        if (contribution > 0) {
            reasons.add(new DecisionReason("CHARGEBACK_HISTORY",
                "Historical chargeback rate adds risk", contribution));
        }
        return contribution;
    }

    private int priorDeclineContribution(final FeatureSnapshot features, final List<DecisionReason> reasons) {
        int contribution = Math.min(20, features.priorDeclines24h() * properties.getScoring().getPriorDeclineWeight());
        if (contribution > 0) {
            reasons.add(new DecisionReason("RECENT_PRIOR_DECLINES",
                "Customer has recent declined attempts", contribution));
        }
        return contribution;
    }

    private int accountAgeContribution(final FeatureSnapshot features, final List<DecisionReason> reasons) {
        if (features.accountAgeDays() < 7) {
            int contribution = 12;
            reasons.add(new DecisionReason("NEW_ACCOUNT",
                "Account is less than 7 days old", contribution));
            return contribution;
        }
        return 0;
    }

    private FraudVerdict verdictFor(final int score) {
        if (score >= properties.getScoring().getDeclineThreshold()) {
            return FraudVerdict.DECLINE;
        }
        if (score >= properties.getScoring().getReviewThreshold()) {
            return FraudVerdict.REVIEW;
        }
        return FraudVerdict.APPROVE;
    }
}
