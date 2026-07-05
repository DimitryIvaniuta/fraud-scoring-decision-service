package com.github.dimitryivaniuta.fraud.enrichment;

import com.github.dimitryivaniuta.fraud.domain.TransactionEnrichmentRequestedEvent;
import com.github.dimitryivaniuta.fraud.service.JsonService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

/**
 * Reactor Kafka consumer that refreshes fraud features outside the synchronous decision path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fraud.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FraudEnrichmentConsumer {

    private final FeatureEnrichmentService enrichmentService;
    private final JsonService jsonService;
    private final ReceiverOptions<String, String> receiverOptions;
    private Disposable subscription;

    /**
     * Starts the enrichment Kafka subscription after the application context is ready.
     */
    @PostConstruct
    public void start() {
        subscription = KafkaReceiver.create(receiverOptions)
            .receive()
            .flatMap(record -> process(record.value())
                .then(record.receiverOffset().commit())
                .onErrorResume(exception -> {
                    log.warn("Unable to process enrichment event offset={} error={}",
                        record.receiverOffset().offset(), exception.toString());
                    return Mono.empty();
                }), 4)
            .subscribe();
    }

    /**
     * Stops the Kafka subscription during shutdown.
     */
    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private Mono<Void> process(final String payload) {
        TransactionEnrichmentRequestedEvent event = jsonService.read(payload, TransactionEnrichmentRequestedEvent.class);
        return enrichmentService.enrich(event)
            .doOnNext(snapshot -> log.debug("Enriched features customerId={} merchantId={}",
                snapshot.customerId(), snapshot.merchantId()))
            .then();
    }
}
