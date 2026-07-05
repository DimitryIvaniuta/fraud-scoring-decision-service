package com.github.dimitryivaniuta.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.fraud.config.JacksonConfiguration;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies canonical request fingerprinting for retry consistency.
 */
class FingerprintServiceTest {

    private final FingerprintService service = new FingerprintService(new JacksonConfiguration().objectMapper());

    /**
     * Equal requests must produce equal SHA-256 fingerprints.
     */
    @Test
    void fingerprintIsStableForSamePayload() {
        TransactionRequest request = request("tx-1", new BigDecimal("100.00"));

        assertThat(service.fingerprint(request)).isEqualTo(service.fingerprint(request));
        assertThat(service.fingerprint(request)).hasSize(64);
    }

    /**
     * Hashing arbitrary strings should avoid leaking raw identifiers into cache keys.
     */
    @Test
    void hashStringReturnsStableSha256() {
        String value = "customer-1:merchant-1";

        assertThat(service.hashString(value)).isEqualTo(service.hashString(value));
        assertThat(service.hashString(value)).hasSize(64);
        assertThat(service.hashString(value)).doesNotContain("customer-1");
    }

    /**
     * Different request content must produce a different fingerprint.
     */
    @Test
    void fingerprintChangesWhenPayloadChanges() {
        String first = service.fingerprint(request("tx-1", new BigDecimal("100.00")));
        String second = service.fingerprint(request("tx-1", new BigDecimal("101.00")));

        assertThat(second).isNotEqualTo(first);
    }

    private TransactionRequest request(final String transactionId, final BigDecimal amount) {
        return new TransactionRequest(transactionId, "customer-1", "merchant-1", amount,
            "PLN", "PL", "ECOMMERCE", "device-1", "203.0.113.10",
            Instant.parse("2026-07-04T10:00:00Z"));
    }
}
