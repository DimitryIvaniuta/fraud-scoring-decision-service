package com.github.dimitryivaniuta.fraud.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for API failures that should be mapped to a specific HTTP status.
 */
public class FraudApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Creates an API exception.
     *
     * @param status HTTP status returned to the client
     * @param message error message
     */
    public FraudApiException(final HttpStatus status, final String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status associated with this failure.
     *
     * @return HTTP status
     */
    public HttpStatus getStatus() {
        return status;
    }
}
