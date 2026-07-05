package com.github.dimitryivaniuta.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Central JSON helper that keeps serialization behavior consistent across API, Redis, and outbox code.
 */
@Service
@RequiredArgsConstructor
public class JsonService {

    private final ObjectMapper objectMapper;

    /**
     * Serializes a value to JSON.
     *
     * @param value value to serialize
     * @return serialized JSON
     */
    public String write(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize JSON", exception);
        }
    }

    /**
     * Deserializes a JSON string into a target type.
     *
     * @param json JSON string
     * @param type target class
     * @param <T> target type
     * @return deserialized value
     */
    public <T> T read(final String json, final Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to deserialize JSON", exception);
        }
    }
}
