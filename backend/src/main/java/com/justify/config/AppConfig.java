package com.justify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.ai.ExtractionParser;
import com.justify.ai.GroqAiClient;
import com.justify.ai.OllamaAiClient;
import com.justify.ai.PromptBuilder;
import com.justify.core.JustificationRenderer;
import com.justify.core.QualityScorer;
import com.justify.core.pipeline.AiClient;
import com.justify.core.pipeline.EvaluationPipeline;
import com.justify.persistence.SubmissionRepository;
import com.justify.service.JustificationService;
import com.justify.template.TemplateRegistry;
import com.justify.template.YamlTemplateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TemplateRegistry templateRegistry(JustifyProperties props) {
        return TemplateRegistry.load(new YamlTemplateParser(), props.templatesDir());
    }

    @Bean
    AiClient aiClient(JustifyProperties props, ObjectMapper mapper, RestClient.Builder builder) {
        JustifyProperties.Ai ai = props.ai();
        PromptBuilder prompts = new PromptBuilder();
        ExtractionParser parser = new ExtractionParser(mapper);
        String provider = ai.provider().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "ollama" -> {
                log.info("AI provider: Ollama {} at {}", ai.ollama().model(), ai.ollama().url());
                yield new OllamaAiClient(builder.clone().baseUrl(ai.ollama().url())
                        .requestFactory(factory(ai.timeout())).build(),
                        ai.ollama().model(), prompts, parser, ai.maxRetries());
            }
            case "groq" -> {
                if (ai.groq().apiKey() == null || ai.groq().apiKey().isBlank()) {
                    throw new IllegalStateException("AI_PROVIDER=groq needs GROQ_API_KEY");
                }
                log.warn("AI provider: Groq {} (cloud). Use dummy data only until security approves.",
                        ai.groq().model());
                yield new GroqAiClient(builder.clone().baseUrl(ai.groq().url())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ai.groq().apiKey())
                        .requestFactory(factory(ai.timeout())).build(),
                        ai.groq().model(), prompts, parser, ai.maxRetries());
            }
            default -> throw new IllegalStateException("Unknown AI_PROVIDER '" + provider + "' (ollama or groq)");
        };
    }

    @Bean
    EvaluationPipeline evaluationPipeline(AiClient aiClient, JustifyProperties props) {
        return new EvaluationPipeline(aiClient, new QualityScorer(), new JustificationRenderer(),
                props.pipeline().maxQuestions(), props.pipeline().maxRounds());
    }

    @Bean
    JustificationService justificationService(EvaluationPipeline pipeline, TemplateRegistry templates,
                                              SubmissionRepository repository, PlatformTransactionManager txm,
                                              ObjectMapper mapper, Clock clock) {
        return new JustificationService(pipeline, templates, repository, new TransactionTemplate(txm), mapper, clock);
    }

    private static SimpleClientHttpRequestFactory factory(Duration timeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(timeout);
        return f;
    }
}
