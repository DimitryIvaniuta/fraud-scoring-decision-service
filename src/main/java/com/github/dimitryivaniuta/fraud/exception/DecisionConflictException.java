package com.github.dimitryivaniuta.fraud.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the same transaction id is retried with a different payload.
 */
public class DecisionConflictException extends FraudApiException {

    /**
     * Creates a conflict exception.
     *
     * @param transactionId conflicting transaction identifier
     */
    public DecisionConflictException(final String transactionId) {
        super(HttpStatus.CONFLICT,
            "Transaction " + transactionId + " already has a decision for a different request payload");
    }
}
