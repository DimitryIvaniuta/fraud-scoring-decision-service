package com.github.dimitryivaniuta.fraud.kafka;

import com.github.dimitryivaniuta.fraud.config.FraudProperties;
import com.github.dimitryivaniuta.fraud.persistence.OutboxEventEntity;
import com.github.dimitryivaniuta.fraud.persistence.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * Publishes committed outbox records to Kafka and retries failures without losing decisions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fraud.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final FraudProperties properties;
    private final KafkaSender<String, String> kafkaSender;
    private final OutboxRepository outboxRepository;

    /**
     * Polls and claims ready outbox events before publishing them to Kafka.
     */
    @Scheduled(fixedDelayString = "${fraud.kafka.outbox-poll-delay:500ms}")
    public void publishReadyEvents() {
        outboxRepository.claimReadyToPublish(
                properties.getKafka().getOutboxPollLimit(),
                properties.getKafka().getOutboxMaxAttempts(),
                properties.getKafka().getOutboxProcessingTtl())
            .flatMap(this::publishOne, properties.getKafka().getOutboxPublishConcurrency())
            .onErrorContinue((throwable, item) -> log.warn("Outbox publishing batch error: {}", throwable.toString()))
            .subscribe();
    }

    private Mono<Void> publishOne(final OutboxEventEntity event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.eventKey(), event.payload());
        SenderRecord<String, String, OutboxEventEntity> senderRecord = SenderRecord.create(record, event);
        return kafkaSender.send(Flux.just(senderRecord))
            .single()
            .flatMap(result -> {
                if (result.exception() != null) {
                    log.warn("Kafka publish failed eventId={} topic={} attempt={} error={}",
                        event.id(), event.topic(), event.attempts(), result.exception().toString());
                    return outboxRepository.markFailed(event, properties.getKafka().getOutboxMaxAttempts());
                }
                log.debug("Kafka publish succeeded eventId={} topic={} partition={} offset={}",
                    event.id(), event.topic(), result.recordMetadata().partition(), result.recordMetadata().offset());
                return outboxRepository.markPublished(event.id());
            })
            .onErrorResume(exception -> {
                log.warn("Kafka publish raised eventId={} topic={} attempt={} error={}",
                    event.id(), event.topic(), event.attempts(), exception.toString());
                return outboxRepository.markFailed(event, properties.getKafka().getOutboxMaxAttempts());
            });
    }
}
