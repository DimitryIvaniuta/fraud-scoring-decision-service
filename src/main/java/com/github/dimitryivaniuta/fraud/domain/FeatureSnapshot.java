package com.github.dimitryivaniuta.fraud.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fraud feature set used by the deterministic scoring engine.
 *
 * @param customerId customer identifier
 * @param merchantId merchant identifier
 * @param averageAmount recent average transaction amount
 * @param chargebackRate historical chargeback ratio from 0 to 1
 * @param highRiskCountry whether the country is considered high risk
 * @param accountAgeDays customer account age in days
 * @param velocity24h number of recent transactions in the last day
 * @param priorDeclines24h recent declined authorizations in the last day
 * @param source feature origin
 * @param refreshedAt timestamp when the snapshot was produced
 */
public record FeatureSnapshot(
    @NotBlank @Size(max = 128) String customerId,
    @NotBlank @Size(max = 128) String merchantId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 4) BigDecimal averageAmount,
    @NotNull @DecimalMin("0.00") @DecimalMax("1.00") @Digits(integer = 1, fraction = 6) BigDecimal chargebackRate,
    boolean highRiskCountry,
    @Min(0) int accountAgeDays,
    @Min(0) int velocity24h,
    @Min(0) int priorDeclines24h,
    @NotNull FeatureSource source,
    @NotNull Instant refreshedAt
) {
}
