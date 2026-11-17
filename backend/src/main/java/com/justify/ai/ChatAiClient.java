package com.justify.ai;

import com.justify.core.AiUnavailableException;
import com.justify.core.Extraction;
import com.justify.core.Template;
import com.justify.core.pipeline.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

/** Shared prompt + retry logic for chat-style providers. Subclasses only make the HTTP call. */
public abstract class ChatAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(ChatAiClient.class);

    private final PromptBuilder promptBuilder;
    private final ExtractionParser parser;
    private final int maxRetries;

    protected ChatAiClient(PromptBuilder promptBuilder, ExtractionParser parser, int maxRetries) {
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.maxRetries = Math.max(0, maxRetries);
    }

    /** Sends system + user message, returns the raw assistant text. */
    protected abstract String chat(String system, String user);

    protected abstract String providerName();

    @Override
    public Extraction extract(Template template, String text) {
        String system = promptBuilder.systemPrompt(template);
        String user = promptBuilder.userPrompt(text);
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return parser.parse(chat(system, user), template);
            } catch (ExtractionParser.MalformedReplyException e) {
                log.warn("{}: unusable reply (attempt {}): {}", providerName(), attempt + 1, e.getMessage());
                last = e;
            } catch (RestClientException e) {
                log.warn("{}: call failed (attempt {}): {}", providerName(), attempt + 1, e.getMessage());
                last = e;
            }
        }
        throw new AiUnavailableException(providerName() + " gave no usable reply", last);
    }
}
