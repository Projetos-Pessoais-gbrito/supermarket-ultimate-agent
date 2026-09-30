package com.supermarketagent.ai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.supermarketagent.ai.AiUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    record Categories(List<Item> items) {
        record Item(int id, String category) {
        }
    }

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void sendsPromptWithSchemaAndParsesTheAnswer() {
        server.expect(requestTo(URL))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("classify"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andRespond(withSuccess(answer("{\"items\":[{\"id\":1,\"category\":\"PADARIA\"}]}"),
                        MediaType.APPLICATION_JSON));

        Categories result = client("test-key").generateJson("classify", SCHEMA, Categories.class);

        assertThat(result.items()).containsExactly(new Categories.Item(1, "PADARIA"));
        server.verify();
    }

    @Test
    void retriesWhenRateLimited() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"items\":[]}"), MediaType.APPLICATION_JSON));

        assertThat(client("test-key").generateJson("classify", SCHEMA, Categories.class).items()).isEmpty();
        server.verify();
    }

    @Test
    void doesNotRetryBadRequests() {
        server.expect(requestTo(URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> client("test-key").generateJson("classify", SCHEMA, Categories.class))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    @Test
    void reportsAnswersThatDoNotMatchTheSchema() {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("not json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("test-key").generateJson("classify", SCHEMA, Categories.class))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void isUnavailableWithoutAnApiKey() {
        GeminiClient client = client("");

        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.generateJson("classify", SCHEMA, Categories.class))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        server.verify();
    }

    private GeminiClient client(String apiKey) {
        GeminiProperties properties = new GeminiProperties(apiKey, "gemini-flash-lite-latest",
                "https://generativelanguage.googleapis.com", Duration.ofSeconds(5), Duration.ZERO, 3, Duration.ZERO);
        return new GeminiClient(builder.build(), properties, JsonMapper.builder().build());
    }

    /** Minimal generateContent response wrapping the model's text. */
    private static String answer(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}],\"role\":\"model\"}}]}";
    }
}
