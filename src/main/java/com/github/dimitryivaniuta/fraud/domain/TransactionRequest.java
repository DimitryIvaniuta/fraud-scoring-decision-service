package com.github.dimitryivaniuta.fraud.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for the synchronous fraud decision API.
 *
 * @param transactionId globally unique transaction identifier used for idempotency
 * @param customerId customer identifier
 * @param merchantId merchant identifier
 * @param amount transaction amount
 * @param currency ISO-4217 currency code
 * @param country ISO-3166 country code of the transaction context
 * @param channel payment channel such as CARD_PRESENT or ECOMMERCE
 * @param deviceId client device identifier, if available
 * @param ipAddress client IP address, if available
 * @param occurredAt business timestamp of the transaction
 */
public record TransactionRequest(
    @NotBlank @Size(max = 128) String transactionId,
    @NotBlank @Size(max = 128) String customerId,
    @NotBlank @Size(max = 128) String merchantId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country,
    @NotBlank @Size(max = 48) String channel,
    @Size(max = 128) String deviceId,
    @Size(max = 64) String ipAddress,
    @NotNull Instant occurredAt
) {
}
