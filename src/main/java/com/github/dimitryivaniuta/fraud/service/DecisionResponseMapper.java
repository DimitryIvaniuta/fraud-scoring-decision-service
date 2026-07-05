package com.github.dimitryivaniuta.fraud.service;

import com.github.dimitryivaniuta.fraud.domain.DecisionResponse;
import com.github.dimitryivaniuta.fraud.persistence.DecisionEntity;
import org.springframework.stereotype.Component;

/**
 * Converts persisted decision entities into REST responses.
 */
@Component
public class DecisionResponseMapper {

    /**
     * Maps a persisted decision to an API response.
     *
     * @param entity persisted decision entity
     * @param cacheHit whether the current request used Redis feature data
     * @param idempotentReplay whether the response reused an existing decision
     * @param decisionTimeMs synchronous decision time in milliseconds
     * @return API response
     */
    public DecisionResponse toResponse(
        final DecisionEntity entity,
        final boolean cacheHit,
        final boolean idempotentReplay,
        final long decisionTimeMs
    ) {
        return new DecisionResponse(
            entity.id(),
            entity.transactionId(),
            entity.modelVersion(),
            entity.verdict(),
            entity.score(),
            entity.reasons(),
            entity.featureSource(),
            cacheHit,
            idempotentReplay,
            decisionTimeMs,
            entity.correlationId(),
            entity.createdAt()
        );
    }
}
