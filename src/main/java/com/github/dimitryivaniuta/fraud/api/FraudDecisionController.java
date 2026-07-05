package com.github.dimitryivaniuta.fraud.api;

import com.github.dimitryivaniuta.fraud.config.CorrelationIdWebFilter;
import com.github.dimitryivaniuta.fraud.domain.DecisionResponse;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import com.github.dimitryivaniuta.fraud.service.FraudDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller for synchronous real-time fraud decisions.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fraud/decisions")
public class FraudDecisionController {

    private final FraudDecisionService fraudDecisionService;

    /**
     * Scores a transaction and persists the decision before returning.
     *
     * @param request transaction request payload
     * @param correlationId optional client correlation id
     * @return persisted decision response
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DecisionResponse> decide(
        @Valid @RequestBody final TransactionRequest request,
        @RequestHeader(name = CorrelationIdWebFilter.CORRELATION_ID_HEADER) final String correlationId
    ) {
        return fraudDecisionService.decide(request, correlationId);
    }

    /**
     * Returns a previously recorded decision by transaction id.
     *
     * @param transactionId transaction identifier
     * @return persisted decision response
     */
    @GetMapping("/{transactionId}")
    public Mono<DecisionResponse> getDecision(@PathVariable final String transactionId) {
        return fraudDecisionService.getDecision(transactionId);
    }

}
