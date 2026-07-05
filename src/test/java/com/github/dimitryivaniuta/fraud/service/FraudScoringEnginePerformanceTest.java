package com.github.dimitryivaniuta.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Smoke performance test for the deterministic in-memory scoring path.
 */
class FraudScoringEnginePerformanceTest {

    /**
     * The pure scoring engine should be far below the 100ms API budget.
     */
    @Test
    @Tag("performance")
    void scoringP95IsBelowOneMillisecond() {
        FraudScoringEngine engine = new FraudScoringEngine(new FraudProperties());
        TransactionRequest request = new TransactionRequest("perf-tx", "customer-perf", "merchant-perf",
            new BigDecimal("300.00"), "PLN", "PL", "ECOMMERCE", "device", "203.0.113.11", Instant.now());
        FeatureSnapshot features = new FeatureSnapshot("customer-perf", "merchant-perf", new BigDecimal("100.00"),
            new BigDecimal("0.01"), false, 400, 4, 0, FeatureSource.REDIS, Instant.now());
        List<Long> times = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) {
            long started = System.nanoTime();
            engine.score(request, features);
            times.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started));
        }

        Collections.sort(times);
        long p95Micros = times.get((int) (times.size() * 0.95));
        assertThat(p95Micros).isLessThan(1_000L);
    }
}
