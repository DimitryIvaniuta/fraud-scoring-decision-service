package com.github.dimitryivaniuta.fraud.persistence;

import com.github.dimitryivaniuta.fraud.domain.OutboxStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable event used by the transactional outbox publisher.
 *
 * @param id outbox event identifier
 * @param aggregateId aggregate identifier used for operational tracing
 * @param topic Kafka topic name
 * @param eventKey Kafka event key
 * @param payload JSON event payload
 * @param status publish status
 * @param attempts number of publish attempts
 * @param availableAt next eligible publish time
 * @param createdAt insert timestamp
 * @param publishedAt publish timestamp, if published
 */
public record OutboxEventEntity(
    UUID id,
    String aggregateId,
    String topic,
    String eventKey,
    String payload,
    OutboxStatus status,
    int attempts,
    Instant availableAt,
    Instant createdAt,
    Instant publishedAt
) {
}
