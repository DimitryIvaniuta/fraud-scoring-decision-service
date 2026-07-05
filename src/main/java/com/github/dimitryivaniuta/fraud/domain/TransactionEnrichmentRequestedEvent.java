package com.github.dimitryivaniuta.fraud.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event that requests asynchronous feature refresh after a synchronous decision.
 *
 * @param eventId unique event identifier
 * @param transactionId transaction identifier
 * @param customerId customer identifier
 * @param merchantId merchant identifier
 * @param amount transaction amount
 * @param currency transaction currency
 * @param country transaction country code
 * @param channel transaction channel
 * @param correlationId request correlation identifier
 * @param occurredAt event creation timestamp
 */
public record TransactionEnrichmentRequestedEvent(
    UUID eventId,
    String transactionId,
    String customerId,
    String merchantId,
    BigDecimal amount,
    String currency,
    String country,
    String channel,
    String correlationId,
    Instant occurredAt
) {
}
