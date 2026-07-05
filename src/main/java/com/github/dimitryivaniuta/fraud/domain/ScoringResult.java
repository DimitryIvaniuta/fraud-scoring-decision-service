package com.github.dimitryivaniuta.fraud.domain;

import java.util.List;

/**
 * Internal result of evaluating deterministic fraud scoring rules.
 *
 * @param modelVersion scoring model version used to produce the result
 * @param verdict final business verdict
 * @param score bounded risk score from 0 to 100
 * @param reasons explainability reasons emitted by active rules
 */
public record ScoringResult(String modelVersion, FraudVerdict verdict, int score, List<DecisionReason> reasons) {
}
