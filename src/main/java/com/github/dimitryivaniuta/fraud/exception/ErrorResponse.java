package com.github.dimitryivaniuta.fraud.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response returned by the REST API.
 *
 * @param timestamp time when the error response was created
 * @param status numeric HTTP status
 * @param error HTTP status reason
 * @param message business-facing error message
 * @param path request path
 * @param validationErrors validation messages, if any
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> validationErrors
) {
}
