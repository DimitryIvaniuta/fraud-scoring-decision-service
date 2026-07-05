package com.github.dimitryivaniuta.fraud.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ensures every HTTP exchange has a correlation id that is echoed back to clients and persisted with decisions.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter {

    /** Header used for request tracing across APIs, logs, and stored decisions. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    /**
     * Adds a correlation id when the caller did not provide one and writes it to the response headers.
     *
     * @param exchange current web exchange
     * @param chain next filter chain entry
     * @return completion signal
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        String correlationId = normalize(exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER));
        ServerHttpRequest request = exchange.getRequest()
            .mutate()
            .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
            .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    private String normalize(final String correlationId) {
        return Optional.ofNullable(correlationId)
            .filter(value -> !value.isBlank())
            .map(String::trim)
            .map(value -> value.length() > MAX_CORRELATION_ID_LENGTH
                ? value.substring(0, MAX_CORRELATION_ID_LENGTH) : value)
            .orElseGet(() -> UUID.randomUUID().toString());
    }
}
