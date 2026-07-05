package com.github.dimitryivaniuta.fraud.enrichment;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.TransactionEnrichmentRequestedEvent;
import com.github.dimitryivaniuta.fraud.service.FeatureCacheService;
import com.github.dimitryivaniuta.fraud.service.FingerprintService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Produces deterministic enriched feature snapshots from asynchronous enrichment events.
 */
@Service
@RequiredArgsConstructor
public class FeatureEnrichmentService {

    private final FeatureCacheService featureCacheService;
    private final FingerprintService fingerprintService;
    private final FraudProperties properties;

    /**
     * Enriches a transaction event and stores the refreshed feature snapshot.
     *
     * @param event enrichment request event
     * @return persisted enriched feature snapshot
     */
    public Mono<FeatureSnapshot> enrich(final TransactionEnrichmentRequestedEvent event) {
        FeatureSnapshot snapshot = buildSnapshot(event);
        return featureCacheService.save(snapshot);
    }

    private FeatureSnapshot buildSnapshot(final TransactionEnrichmentRequestedEvent event) {
        int hash = fingerprintService.stablePositiveHash(event.customerId() + ':' + event.merchantId());
        boolean highRiskCountry = properties.getScoring().getHighRiskCountries()
            .stream()
            .anyMatch(country -> country.equalsIgnoreCase(event.country()));
        BigDecimal averageAmount = event.amount()
            .multiply(BigDecimal.valueOf(0.65d + ((hash % 350) / 100.0d)))
            .max(BigDecimal.TEN)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal chargebackRate = BigDecimal.valueOf(hash % 120)
            .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        return new FeatureSnapshot(
            event.customerId(),
            event.merchantId(),
            averageAmount,
            chargebackRate,
            highRiskCountry,
            5 + hash % 2500,
            1 + hash % 14,
            hash % 5,
            FeatureSource.ENRICHED,
            Instant.now()
        );
    }
}
