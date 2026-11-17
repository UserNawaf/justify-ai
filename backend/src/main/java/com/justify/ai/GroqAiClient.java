package com.justify.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Llama hosted on Groq (OpenAI-compatible API). Cloud service: only dummy data until security approves (D18).
 */
public class GroqAiClient extends ChatAiClient {

    private final RestClient http;
    private final String model;

    public GroqAiClient(RestClient http, String model, PromptBuilder promptBuilder, ExtractionParser parser,
                        int maxRetries) {
        super(promptBuilder, parser, maxRetries);
        this.http = http;
        this.model = model;
    }

    @Override
    protected String chat(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        JsonNode reply = http.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode content = reply == null ? null : reply.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isTextual()) {
            throw new RestClientException("Groq reply has no choices[0].message.content");
        }
        return content.asText();
    }

    @Override
    protected String providerName() {
        return "groq/" + model;
    }
}
