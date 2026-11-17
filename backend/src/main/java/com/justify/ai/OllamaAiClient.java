package com.justify.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Local Llama through Ollama's /api/chat in JSON mode. Data stays on the machine. */
public class OllamaAiClient extends ChatAiClient {

    private final RestClient http;
    private final String model;

    public OllamaAiClient(RestClient http, String model, PromptBuilder promptBuilder, ExtractionParser parser,
                          int maxRetries) {
        super(promptBuilder, parser, maxRetries);
        this.http = http;
        this.model = model;
    }

    @Override
    protected String chat(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0.1),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        JsonNode reply = http.post().uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (reply == null || !reply.path("message").path("content").isTextual()) {
            throw new RestClientException("Ollama reply has no message.content");
        }
        return reply.path("message").path("content").asText();
    }

    @Override
    protected String providerName() {
        return "ollama/" + model;
    }
}
