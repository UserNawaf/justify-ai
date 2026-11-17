package com.justify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "justify")
public record JustifyProperties(Pipeline pipeline, Ai ai, Security security, String templatesDir, Webhook webhook) {

    public JustifyProperties {
        if (pipeline == null) {
            pipeline = new Pipeline(3, 3);
        }
        if (ai == null) {
            ai = new Ai(null, null, null, null, 2);
        }
        if (security == null) {
            security = new Security(null, null);
        }
        if (webhook == null) {
            webhook = new Webhook(null, null, null);
        }
    }

    /** Outbound webhook when a submission closes. Off when url is empty. */
    public record Webhook(String url, String secret, Duration timeout) {
        public Webhook {
            if (timeout == null) timeout = Duration.ofSeconds(10);
        }

        public boolean enabled() {
            return url != null && !url.isBlank();
        }

        @Override
        public String toString() {
            return "Webhook[url=" + url + ", secret=" + (secret == null || secret.isBlank() ? "unset" : "***") + "]";
        }
    }

    /** D5: at most 3 questions per round, at most 3 rounds. */
    public record Pipeline(int maxQuestions, int maxRounds) {
        public Pipeline {
            if (maxQuestions <= 0) maxQuestions = 3;
            if (maxRounds <= 0) maxRounds = 3;
        }
    }

    public record Ai(String provider, Ollama ollama, Groq groq, Duration timeout, int maxRetries) {
        public Ai {
            if (provider == null || provider.isBlank()) provider = "ollama";
            if (ollama == null) ollama = new Ollama(null, null);
            if (groq == null) groq = new Groq(null, null, null);
            if (timeout == null) timeout = Duration.ofSeconds(120);
            if (maxRetries < 0) maxRetries = 2;
        }
    }

    public record Ollama(String url, String model) {
        public Ollama {
            if (url == null || url.isBlank()) url = "http://localhost:11434";
            if (model == null || model.isBlank()) model = "llama3.1:8b";
        }
    }

    public record Groq(String url, String apiKey, String model) {
        public Groq {
            if (url == null || url.isBlank()) url = "https://api.groq.com/openai/v1";
            if (model == null || model.isBlank()) model = "llama-3.3-70b-versatile";
        }

        @Override
        public String toString() {
            return "Groq[url=" + url + ", model=" + model + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "unset" : "***") + "]";
        }
    }

    public record Security(String apiKey, List<String> corsOrigins) {
        public Security {
            if (corsOrigins == null) corsOrigins = List.of();
        }

        @Override
        public String toString() {
            return "Security[apiKey=" + (apiKey == null || apiKey.isBlank() ? "off" : "***") + ", corsOrigins=" + corsOrigins + "]";
        }
    }
}
