package com.github.dimitryivaniuta.fraud.persistence;

import java.util.UUID;

/**
 * Command object used to insert an event into the outbox table.
 *
 * @param id event identifier
 * @param aggregateId aggregate identifier
 * @param topic target Kafka topic
 * @param eventKey Kafka event key
 * @param payload serialized JSON payload
 */
public record OutboxEventInsert(
    UUID id,
    String aggregateId,
    String topic,
    String eventKey,
    String payload
) {
}
