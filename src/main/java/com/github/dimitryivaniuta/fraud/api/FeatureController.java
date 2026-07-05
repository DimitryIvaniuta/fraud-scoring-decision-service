package com.github.dimitryivaniuta.fraud.api;

import com.github.dimitryivaniuta.fraud.domain.FeatureSnapshot;
import com.github.dimitryivaniuta.fraud.service.FeatureCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Operational API for seeding or refreshing feature snapshots during local and integration testing.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fraud/features")
public class FeatureController {

    private final FeatureCacheService featureCacheService;

    /**
     * Upserts a feature snapshot to PostgreSQL and Redis.
     *
     * @param snapshot feature snapshot request
     * @return saved feature snapshot
     */
    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<FeatureSnapshot> upsert(@Valid @RequestBody final FeatureSnapshot snapshot) {
        return featureCacheService.save(snapshot);
    }
}
