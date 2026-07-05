package com.github.dimitryivaniuta.fraud.service;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FeatureSource;
import com.github.dimitryivaniuta.fraud.domain.ResolvedFeatures;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import com.github.dimitryivaniuta.fraud.persistence.FeatureRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Retrieves feature data from Redis first, then PostgreSQL, and finally deterministic fallback values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureCacheService {

    private final FeatureRepository featureRepository;
    private final FraudProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final FingerprintService fingerprintService;
    private final JsonService jsonService;

    /**
     * Resolves features for a transaction with a bounded latency budget.
     *
     * @param request transaction decision request
     * @return resolved feature data and cache hit flag
     */
    public Mono<ResolvedFeatures> resolve(final TransactionRequest request) {
        String key = key(request.customerId(), request.merchantId());
        return readFromRedis(key)
            .map(snapshot -> new ResolvedFeatures(snapshot, true))
            .switchIfEmpty(readFromPostgres(request)
                .flatMap(snapshot -> writeToRedis(snapshot).thenReturn(new ResolvedFeatures(snapshot, false))))
            .switchIfEmpty(Mono.fromSupplier(() -> new ResolvedFeatures(fallback(request), false)))
            .timeout(properties.getCache().getOperationTimeout().multipliedBy(2))
            .onErrorResume(exception -> {
                log.warn("Feature resolution failed, using fallback: {}", exception.toString());
                return Mono.just(new ResolvedFeatures(fallback(request), false));
            });
    }

    /**
     * Saves a feature snapshot to both PostgreSQL and Redis.
     *
     * @param snapshot feature snapshot
     * @return saved snapshot
     */
    public Mono<FeatureSnapshot> save(final FeatureSnapshot snapshot) {
        return featureRepository.upsert(snapshot)
            .flatMap(saved -> writeToRedis(saved).thenReturn(saved));
    }

    private Mono<FeatureSnapshot> readFromRedis(final String key) {
        return redisTemplate.opsForValue()
            .get(key)
            .timeout(properties.getCache().getOperationTimeout())
            .map(json -> jsonService.read(json, FeatureSnapshot.class))
            .onErrorResume(exception -> {
                log.debug("Redis feature miss or read failure for key {}: {}", key, exception.toString());
                return Mono.empty();
            });
    }

    private Mono<FeatureSnapshot> readFromPostgres(final TransactionRequest request) {
        return featureRepository.find(request.customerId(), request.merchantId())
            .map(snapshot -> new FeatureSnapshot(
                snapshot.customerId(),
                snapshot.merchantId(),
                snapshot.averageAmount(),
                snapshot.chargebackRate(),
                snapshot.highRiskCountry(),
                snapshot.accountAgeDays(),
                snapshot.velocity24h(),
                snapshot.priorDeclines24h(),
                FeatureSource.POSTGRES,
                snapshot.refreshedAt()
            ))
            .timeout(properties.getCache().getOperationTimeout())
            .onErrorResume(exception -> Mono.empty());
    }

    private Mono<Boolean> writeToRedis(final FeatureSnapshot snapshot) {
        String key = key(snapshot.customerId(), snapshot.merchantId());
        return redisTemplate.opsForValue()
            .set(key, jsonService.write(snapshot), properties.getCache().getTtl())
            .timeout(properties.getCache().getOperationTimeout())
            .onErrorReturn(false);
    }

    private FeatureSnapshot fallback(final TransactionRequest request) {
        return new FeatureSnapshot(
            request.customerId(),
            request.merchantId(),
            properties.getCache().getFallbackAverageAmount(),
            BigDecimal.ZERO,
            false,
            properties.getCache().getFallbackAccountAgeDays(),
            0,
            0,
            FeatureSource.FALLBACK,
            Instant.now()
        );
    }

    private String key(final String customerId, final String merchantId) {
        return "fraud:features:" + fingerprintService.hashString(customerId + ":" + merchantId);
    }
}
