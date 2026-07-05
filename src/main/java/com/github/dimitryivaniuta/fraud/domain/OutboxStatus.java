package com.github.dimitryivaniuta.fraud.domain;

/**
 * Delivery state of an outbox event.
 */
public enum OutboxStatus {
    /** Event is ready to be published. */
    PENDING,

    /** Event is currently claimed by one service instance for publishing. */
    PROCESSING,

    /** Previous publish attempt failed and the event is retryable. */
    FAILED,

    /** Event exceeded the retry budget and requires operational investigation. */
    DEAD_LETTER,

    /** Event has been successfully sent to Kafka. */
    PUBLISHED
}
