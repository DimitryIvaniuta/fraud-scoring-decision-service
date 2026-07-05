package com.github.dimitryivaniuta.fraud.persistence;

import com.github.dimitryivaniuta.fraud.domain.OutboxStatus;
import io.r2dbc.spi.Row;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive persistence gateway for the transactional outbox table.
 */
@Repository
@RequiredArgsConstructor
public class OutboxRepository {

    private final DatabaseClient databaseClient;

    /**
     * Atomically claims ready outbox events using PostgreSQL row locking so multiple service instances do not publish
     * the same row at the same time. Expired PROCESSING rows are also recoverable by this query.
     *
     * @param limit maximum number of events to claim
     * @param maxAttempts maximum allowed publish attempts before dead lettering
     * @param processingTtl duration after which a claimed row can be recovered by another instance
     * @return claimed events ordered by creation time
     */
    public Flux<OutboxEventEntity> claimReadyToPublish(
        final int limit,
        final int maxAttempts,
        final Duration processingTtl
    ) {
        Instant lockedUntil = Instant.now().plus(processingTtl);
        return databaseClient.sql("""
            WITH exhausted AS (
                UPDATE outbox_events
                   SET status = 'DEAD_LETTER', available_at = now()
                 WHERE status = 'PROCESSING'
                   AND available_at <= now()
                   AND attempts >= :maxAttempts
                RETURNING id
            ),
            ready AS (
                SELECT id
                  FROM outbox_events
                 WHERE ((status IN ('PENDING', 'FAILED') AND available_at <= now())
                    OR (status = 'PROCESSING' AND available_at <= now()))
                   AND attempts < :maxAttempts
                 ORDER BY created_at ASC
                 LIMIT :limit
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events AS event
               SET status = 'PROCESSING',
                   attempts = event.attempts + 1,
                   available_at = :lockedUntil
              FROM ready
             WHERE event.id = ready.id
            RETURNING event.id,
                      event.aggregate_id,
                      event.topic,
                      event.event_key,
                      event.payload::text AS payload,
                      event.status,
                      event.attempts,
                      event.available_at,
                      event.created_at,
                      event.published_at
            """)
            .bind("limit", limit)
            .bind("maxAttempts", maxAttempts)
            .bind("lockedUntil", lockedUntil)
            .map((row, metadata) -> map(row))
            .all();
    }

    /**
     * Inserts a new outbox event with PENDING status.
     *
     * @param insert event insert command
     * @return inserted outbox event
     */
    public Mono<OutboxEventEntity> insert(final OutboxEventInsert insert) {
        Instant now = Instant.now();
        OutboxEventEntity entity = new OutboxEventEntity(
            insert.id(),
            insert.aggregateId(),
            insert.topic(),
            insert.eventKey(),
            insert.payload(),
            OutboxStatus.PENDING,
            0,
            now,
            now,
            null
        );
        return databaseClient.sql("""
            INSERT INTO outbox_events (
                id, aggregate_id, topic, event_key, payload, status, attempts, available_at, created_at
            ) VALUES (
                :id, :aggregateId, :topic, :eventKey, CAST(:payload AS JSONB), :status, :attempts, :availableAt, :createdAt
            )
            """)
            .bind("id", entity.id())
            .bind("aggregateId", entity.aggregateId())
            .bind("topic", entity.topic())
            .bind("eventKey", entity.eventKey())
            .bind("payload", entity.payload())
            .bind("status", entity.status().name())
            .bind("attempts", entity.attempts())
            .bind("availableAt", entity.availableAt())
            .bind("createdAt", entity.createdAt())
            .fetch()
            .rowsUpdated()
            .thenReturn(entity);
    }

    /**
     * Marks an outbox event as dead letter after the retry budget is exhausted.
     *
     * @param eventId outbox event identifier
     * @return completion signal
     */
    public Mono<Void> markDeadLetter(final UUID eventId) {
        return databaseClient.sql("""
            UPDATE outbox_events
               SET status = 'DEAD_LETTER', available_at = now()
             WHERE id = :eventId AND status = 'PROCESSING'
            """)
            .bind("eventId", eventId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    /**
     * Marks an outbox event as failed and schedules retry with exponential backoff.
     *
     * @param event event that failed publishing
     * @param maxAttempts maximum allowed attempts before dead lettering
     * @return completion signal
     */
    public Mono<Void> markFailed(final OutboxEventEntity event, final int maxAttempts) {
        if (event.attempts() >= maxAttempts) {
            return markDeadLetter(event.id());
        }
        Duration backoff = Duration.ofSeconds(Math.min(60, (long) Math.pow(2, Math.min(event.attempts(), 6))));
        return databaseClient.sql("""
            UPDATE outbox_events
               SET status = 'FAILED', available_at = :availableAt
             WHERE id = :eventId AND status = 'PROCESSING'
            """)
            .bind("availableAt", Instant.now().plus(backoff))
            .bind("eventId", event.id())
            .fetch()
            .rowsUpdated()
            .then();
    }

    /**
     * Marks an outbox event as published after Kafka acknowledgement.
     *
     * @param eventId outbox event identifier
     * @return completion signal
     */
    public Mono<Void> markPublished(final UUID eventId) {
        return databaseClient.sql("""
            UPDATE outbox_events
               SET status = 'PUBLISHED', published_at = now(), available_at = now()
             WHERE id = :eventId AND status = 'PROCESSING'
            """)
            .bind("eventId", eventId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private OutboxEventEntity map(final Row row) {
        return new OutboxEventEntity(
            row.get("id", UUID.class),
            row.get("aggregate_id", String.class),
            row.get("topic", String.class),
            row.get("event_key", String.class),
            row.get("payload", String.class),
            OutboxStatus.valueOf(row.get("status", String.class)),
            row.get("attempts", Integer.class),
            row.get("available_at", Instant.class),
            row.get("created_at", Instant.class),
            row.get("published_at", Instant.class)
        );
    }
}
