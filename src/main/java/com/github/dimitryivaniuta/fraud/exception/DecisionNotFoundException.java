package com.github.dimitryivaniuta.fraud.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a requested decision cannot be found.
 */
public class DecisionNotFoundException extends FraudApiException {

    /**
     * Creates a not-found exception.
     *
     * @param transactionId missing transaction identifier
     */
    public DecisionNotFoundException(final String transactionId) {
        super(HttpStatus.NOT_FOUND, "Decision was not found for transaction " + transactionId);
    }
}
