package com.supermarketagent.ai;

import java.util.Map;

/**
 * Provider-agnostic access to a generative AI model (ADR 0005).
 *
 * <p>Never send personal data through this interface: only product descriptions and aggregated metrics.
 */
public interface AiClient {

    /** False when no provider is configured (e.g. no API key); callers should skip AI features. */
    boolean isAvailable();

    /**
     * Asks the model for JSON that follows {@code jsonSchema} and maps it to {@code type}.
     *
     * @throws AiUnavailableException when the provider cannot answer right now
     */
    <T> T generateJson(String prompt, Map<String, Object> jsonSchema, Class<T> type);
}
