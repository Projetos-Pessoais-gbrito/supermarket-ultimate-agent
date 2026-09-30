package com.supermarketagent.ai.gemini;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param apiKey      Google AI Studio key (env {@code GEMINI_API_KEY}); AI features are off when blank
 * @param model       model id or alias; Flash-Lite has the most generous free quota
 * @param minInterval minimum time between calls, to stay under the free tier requests-per-minute limit
 */
@ConfigurationProperties("app.ai.gemini")
public record GeminiProperties(
        String apiKey,
        @DefaultValue("gemini-flash-lite-latest") String model,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("5s") Duration minInterval,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("10s") Duration retryBackoff) {

    boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
