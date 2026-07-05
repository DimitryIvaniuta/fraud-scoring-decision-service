package com.github.dimitryivaniuta.fraud.exception;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Maps validation and domain exceptions to stable JSON responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Handles business exceptions with predefined status codes.
     *
     * @param exception domain API exception
     * @param exchange current exchange
     * @return error response
     */
    @ExceptionHandler(FraudApiException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleFraudApiException(
        final FraudApiException exception,
        final ServerWebExchange exchange
    ) {
        return Mono.just(toResponse(exception.getStatus(), exception.getMessage(), exchange, List.of()));
    }

    /**
     * Handles request validation failures from WebFlux validation.
     *
     * @param exception validation exception
     * @param exchange current exchange
     * @return validation error response
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
        final WebExchangeBindException exception,
        final ServerWebExchange exchange
    ) {
        List<String> errors = exception.getBindingResult()
            .getAllErrors()
            .stream()
            .map(error -> error instanceof FieldError fieldError
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : error.getDefaultMessage())
            .toList();
        return Mono.just(toResponse(HttpStatus.BAD_REQUEST, "Request validation failed", exchange, errors));
    }

    /**
     * Handles unexpected exceptions without leaking internal implementation details.
     *
     * @param exception unexpected exception
     * @param exchange current exchange
     * @return generic error response
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnexpectedException(
        final Exception exception,
        final ServerWebExchange exchange
    ) {
        return Mono.just(toResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected service error", exchange, List.of(exception.getClass().getSimpleName())));
    }

    private ResponseEntity<ErrorResponse> toResponse(
        final HttpStatus status,
        final String message,
        final ServerWebExchange exchange,
        final List<String> validationErrors
    ) {
        ErrorResponse response = new ErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            exchange.getRequest().getPath().value(),
            validationErrors
        );
        return ResponseEntity.status(status).body(response);
    }
}
