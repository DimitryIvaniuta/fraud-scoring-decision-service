package com.github.dimitryivaniuta.fraud.domain;

/**
 * Feature lookup result containing both data and cache hit metadata.
 *
 * @param snapshot feature values used for scoring
 * @param cacheHit whether Redis served the feature values
 */
public record ResolvedFeatures(FeatureSnapshot snapshot, boolean cacheHit) {
}
