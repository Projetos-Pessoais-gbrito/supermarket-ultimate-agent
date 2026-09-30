package com.supermarketagent.ai.gemini;

import com.supermarketagent.ai.AiClient;
import com.supermarketagent.ai.AiUnavailableException;
import com.supermarketagent.receipt.provider.RequestThrottle;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Google Gemini via the {@code generateContent} REST API with JSON-schema constrained output. */
public class GeminiClient implements AiClient {

    private final RestClient client;
    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;
    private final RequestThrottle throttle;

    public GeminiClient(RestClient client, GeminiProperties properties, JsonMapper jsonMapper) {
        this.client = client;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.throttle = new RequestThrottle(properties.minInterval());
    }

    @Override
    public boolean isAvailable() {
        return properties.configured();
    }

    @Override
    public <T> T generateJson(String prompt, Map<String, Object> jsonSchema, Class<T> type) {
        if (!isAvailable()) {
            throw new AiUnavailableException("Gemini is not configured (set GEMINI_API_KEY)");
        }
        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseJsonSchema", jsonSchema));

        String text = firstCandidateText(call(request));
        try {
            return jsonMapper.readValue(text, type);
        } catch (JacksonException e) {
            throw new AiUnavailableException("Gemini returned JSON that does not match the schema", e);
        }
    }

    private GenerateContentResponse call(Map<String, Object> request) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                throttle.acquire();
                return client.post()
                        .uri("/v1beta/models/{model}:generateContent", properties.model())
                        .header("x-goog-api-key", properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GenerateContentResponse.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                    // Bad key or request; retrying will not help. Never log the request body.
                    throw new AiUnavailableException("Gemini rejected the request: " + e.getStatusCode(), e);
                }
                lastFailure = e;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiUnavailableException("Interrupted while waiting to call Gemini", e);
            }
            backOff(attempt);
        }
        throw new AiUnavailableException("Gemini unavailable after " + properties.maxAttempts() + " attempts",
                lastFailure);
    }

    private void backOff(int attempt) {
        if (attempt == properties.maxAttempts()) {
            return;
        }
        try {
            Thread.sleep(properties.retryBackoff().multipliedBy(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted while waiting to retry Gemini", e);
        }
    }

    private static String firstCandidateText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AiUnavailableException("Gemini returned no answer");
        }
        var content = response.candidates().getFirst().content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new AiUnavailableException("Gemini answer has no content");
        }
        return content.parts().getFirst().text();
    }

    record GenerateContentResponse(List<Candidate> candidates) {

        record Candidate(Content content) {
        }

        record Content(List<Part> parts) {
        }

        record Part(String text) {
        }
    }
}
