package com.github.dimitryivaniuta.fraud.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Human-readable explanation item that contributed to a fraud score.
 *
 * @param code stable machine-readable reason code
 * @param message concise business explanation
 * @param contribution score contribution between 0 and 100
 */
public record DecisionReason(
    @NotBlank String code,
    @NotBlank String message,
    @Min(0) @Max(100) int contribution
) {
}
