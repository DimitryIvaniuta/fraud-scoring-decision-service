package com.github.dimitryivaniuta.fraud.service;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.domain.DecisionResponse;
import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.domain.FraudDecisionMadeEvent;
import com.github.dimitryivaniuta.fraud.domain.ResolvedFeatures;
import com.github.dimitryivaniuta.fraud.domain.ScoringResult;
import com.github.dimitryivaniuta.fraud.domain.TransactionEnrichmentRequestedEvent;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import com.github.dimitryivaniuta.fraud.exception.DecisionConflictException;
import com.github.dimitryivaniuta.fraud.exception.DecisionNotFoundException;
import com.github.dimitryivaniuta.fraud.persistence.DecisionEntity;
import com.github.dimitryivaniuta.fraud.persistence.DecisionRepository;
import com.github.dimitryivaniuta.fraud.persistence.OutboxEventInsert;
import com.github.dimitryivaniuta.fraud.persistence.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Coordinates idempotent synchronous scoring, durable persistence, and asynchronous event publication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDecisionService {

    private final DecisionRepository decisionRepository;
    private final DecisionResponseMapper decisionResponseMapper;
    private final FeatureCacheService featureCacheService;
    private final FingerprintService fingerprintService;
    private final FraudProperties properties;
    private final FraudScoringEngine scoringEngine;
    private final JsonService jsonService;
    private final MeterRegistry meterRegistry;
    private final OutboxRepository outboxRepository;
    private final TransactionalOperator transactionalOperator;

    /**
     * Scores a transaction and returns only after the decision and outbox records are committed.
     *
     * @param request transaction decision request
     * @param correlationId request correlation identifier
     * @return persisted fraud decision response
     */
    public Mono<DecisionResponse> decide(final TransactionRequest request, final String correlationId) {
        long started = System.nanoTime();
        String fingerprint = fingerprintService.fingerprint(request);
        return decisionRepository.findByTransactionId(request.transactionId())
            .flatMap(existing -> ensureSameRequest(existing, fingerprint)
                .thenReturn(decisionResponseMapper.toResponse(existing, false, true, elapsedMs(started))))
            .switchIfEmpty(Mono.defer(() -> createDecision(request, fingerprint, correlationId, started)))
            .doOnNext(response -> recordMetrics(response, elapsedMs(started)))
            .doOnNext(response -> log.info("Fraud decision transactionId={} verdict={} score={} reasons={} correlationId={}",
                response.transactionId(), response.verdict(), response.score(), response.reasons(), response.correlationId()));
    }

    /**
     * Looks up a previously persisted decision.
     *
     * @param transactionId transaction identifier
     * @return persisted decision response
     */
    public Mono<DecisionResponse> getDecision(final String transactionId) {
        return decisionRepository.findByTransactionId(transactionId)
            .map(entity -> decisionResponseMapper.toResponse(entity, false, false, 0))
            .switchIfEmpty(Mono.error(new DecisionNotFoundException(transactionId)));
    }

    private Mono<DecisionResponse> createDecision(
        final TransactionRequest request,
        final String fingerprint,
        final String correlationId,
        final long started
    ) {
        return featureCacheService.resolve(request)
            .flatMap(features -> persistDecisionAndEvents(request, fingerprint, correlationId, features))
            .map(entityWithCache -> decisionResponseMapper.toResponse(
                entityWithCache.decision(), entityWithCache.cacheHit(), entityWithCache.idempotentReplay(), elapsedMs(started)));
    }

    private Mono<DecisionWithCache> persistDecisionAndEvents(
        final TransactionRequest request,
        final String fingerprint,
        final String correlationId,
        final ResolvedFeatures resolvedFeatures
    ) {
        FeatureSnapshot features = resolvedFeatures.snapshot();
        ScoringResult scoring = scoringEngine.score(request, features);
        Instant now = Instant.now();
        DecisionEntity decision = new DecisionEntity(
            UUID.randomUUID(),
            request.transactionId(),
            fingerprint,
            scoring.modelVersion(),
            scoring.verdict(),
            scoring.score(),
            scoring.reasons(),
            request,
            features,
            features.source(),
            correlationId,
            now
        );

        FraudDecisionMadeEvent decisionEvent = new FraudDecisionMadeEvent(
            UUID.randomUUID(),
            decision.id(),
            request.transactionId(),
            request.customerId(),
            request.merchantId(),
            decision.modelVersion(),
            decision.verdict(),
            decision.score(),
            decision.reasons(),
            decision.featureSource(),
            correlationId,
            now
        );
        TransactionEnrichmentRequestedEvent enrichmentEvent = new TransactionEnrichmentRequestedEvent(
            UUID.randomUUID(),
            request.transactionId(),
            request.customerId(),
            request.merchantId(),
            request.amount(),
            request.currency(),
            request.country(),
            request.channel(),
            correlationId,
            now
        );
        Mono<DecisionWithCache> transaction = decisionRepository.insert(decision)
            .flatMap(saved -> outboxRepository.insert(new OutboxEventInsert(
                    decisionEvent.eventId(), saved.transactionId(), properties.getKafka().getDecisionTopic(),
                    saved.transactionId(), jsonService.write(decisionEvent)))
                .then(outboxRepository.insert(new OutboxEventInsert(
                    enrichmentEvent.eventId(), saved.transactionId(), properties.getKafka().getEnrichmentTopic(),
                    saved.transactionId(), jsonService.write(enrichmentEvent))))
                .thenReturn(new DecisionWithCache(saved, resolvedFeatures.cacheHit(), false)));
        return transactionalOperator.transactional(transaction)
            .onErrorResume(DuplicateKeyException.class, exception -> decisionRepository
                .findByTransactionId(request.transactionId())
                .flatMap(existing -> ensureSameRequest(existing, fingerprint)
                    .thenReturn(new DecisionWithCache(existing, false, true))));
    }

    private Mono<Void> ensureSameRequest(final DecisionEntity existing, final String fingerprint) {
        if (existing.requestFingerprint().equals(fingerprint)) {
            return Mono.empty();
        }
        return Mono.error(new DecisionConflictException(existing.transactionId()));
    }

    private void recordMetrics(final DecisionResponse response, final long decisionTimeMs) {
        Timer.builder("fraud.decision.latency")
            .description("Synchronous fraud decision API latency")
            .tag("verdict", response.verdict().name())
            .tag("featureSource", response.featureSource().name())
            .tag("idempotentReplay", Boolean.toString(response.idempotentReplay()))
            .publishPercentileHistogram()
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(java.time.Duration.ofMillis(decisionTimeMs));
    }

    private long elapsedMs(final long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private record DecisionWithCache(DecisionEntity decision, boolean cacheHit, boolean idempotentReplay) {
    }
}
