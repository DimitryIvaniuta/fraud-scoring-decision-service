package com.github.dimitryivaniuta.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.fraud.domain.TransactionRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds canonical SHA-256 fingerprints for idempotent transaction decision requests.
 */
@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final ObjectMapper objectMapper;

    /**
     * Creates a deterministic fingerprint for a transaction request.
     *
     * @param request transaction decision request
     * @return lowercase hexadecimal SHA-256 hash
     */
    public String fingerprint(final TransactionRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return sha256Hex(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint request", exception);
        }
    }

    /**
     * Creates a lowercase SHA-256 digest for arbitrary strings such as cache-key components.
     *
     * @param value source value
     * @return lowercase hexadecimal SHA-256 hash
     */
    public String hashString(final String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(final byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Creates a stable string hash for simple values used by deterministic enrichment.
     *
     * @param value input string
     * @return positive integer hash
     */
    public int stablePositiveHash(final String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int hash = 0;
        for (byte current : bytes) {
            hash = 31 * hash + current;
        }
        return Math.abs(hash == Integer.MIN_VALUE ? 0 : hash);
    }
}
