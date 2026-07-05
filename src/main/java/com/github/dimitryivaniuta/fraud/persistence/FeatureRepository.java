package com.github.dimitryivaniuta.fraud.persistence;

import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive durable feature store used as a fallback under Redis misses.
 */
@Repository
@RequiredArgsConstructor
public class FeatureRepository {

    private final DatabaseClient databaseClient;

    /**
     * Finds the latest feature snapshot for a customer and merchant pair.
     *
     * @param customerId customer identifier
     * @param merchantId merchant identifier
     * @return stored feature snapshot, if present
     */
    public Mono<FeatureSnapshot> find(final String customerId, final String merchantId) {
        return databaseClient.sql("""
            SELECT customer_id, merchant_id, average_amount, chargeback_rate, high_risk_country,
                   account_age_days, velocity_24h, prior_declines_24h, source, refreshed_at
              FROM transaction_features
             WHERE customer_id = :customerId AND merchant_id = :merchantId
            """)
            .bind("customerId", customerId)
            .bind("merchantId", merchantId)
            .map((row, metadata) -> map(row))
            .one();
    }

    /**
     * Inserts or updates a feature snapshot.
     *
     * @param snapshot snapshot to persist
     * @return persisted snapshot
     */
    public Mono<FeatureSnapshot> upsert(final FeatureSnapshot snapshot) {
        return databaseClient.sql("""
            INSERT INTO transaction_features (
                customer_id, merchant_id, average_amount, chargeback_rate, high_risk_country,
                account_age_days, velocity_24h, prior_declines_24h, source, refreshed_at
            ) VALUES (
                :customerId, :merchantId, :averageAmount, :chargebackRate, :highRiskCountry,
                :accountAgeDays, :velocity24h, :priorDeclines24h, :source, :refreshedAt
            )
            ON CONFLICT (customer_id, merchant_id) DO UPDATE SET
                average_amount = EXCLUDED.average_amount,
                chargeback_rate = EXCLUDED.chargeback_rate,
                high_risk_country = EXCLUDED.high_risk_country,
                account_age_days = EXCLUDED.account_age_days,
                velocity_24h = EXCLUDED.velocity_24h,
                prior_declines_24h = EXCLUDED.prior_declines_24h,
                source = EXCLUDED.source,
                refreshed_at = EXCLUDED.refreshed_at
            """)
            .bind("customerId", snapshot.customerId())
            .bind("merchantId", snapshot.merchantId())
            .bind("averageAmount", snapshot.averageAmount())
            .bind("chargebackRate", snapshot.chargebackRate())
            .bind("highRiskCountry", snapshot.highRiskCountry())
            .bind("accountAgeDays", snapshot.accountAgeDays())
            .bind("velocity24h", snapshot.velocity24h())
            .bind("priorDeclines24h", snapshot.priorDeclines24h())
            .bind("source", snapshot.source().name())
            .bind("refreshedAt", snapshot.refreshedAt())
            .fetch()
            .rowsUpdated()
            .thenReturn(snapshot);
    }

    private FeatureSnapshot map(final Row row) {
        return new FeatureSnapshot(
            row.get("customer_id", String.class),
            row.get("merchant_id", String.class),
            row.get("average_amount", BigDecimal.class),
            row.get("chargeback_rate", BigDecimal.class),
            row.get("high_risk_country", Boolean.class),
            row.get("account_age_days", Integer.class),
            row.get("velocity_24h", Integer.class),
            row.get("prior_declines_24h", Integer.class),
            FeatureSource.valueOf(row.get("source", String.class)),
            row.get("refreshed_at", java.time.Instant.class)
        );
    }
}
