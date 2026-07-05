package com.github.dimitryivaniuta.fraud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures JSON serialization used by APIs, fingerprints, Redis snapshots, and outbox payloads.
 */
@Configuration
public class JacksonConfiguration {

    /**
     * Creates a primary mapper with deterministic map ordering for stable fingerprints.
     *
     * @return configured object mapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
}
