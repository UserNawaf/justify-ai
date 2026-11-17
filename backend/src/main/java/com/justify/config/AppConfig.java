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
import com.justify.firewall.FirewallFormValidator;
import com.justify.firewall.FirewallRequestRepository;
import com.justify.firewall.FirewallRequestService;
import com.justify.firewall.FirewallStatusUpdater;
import com.justify.notify.WebhookNotifier;
import com.justify.persistence.FeedbackRepository;
import com.justify.persistence.SubmissionRepository;
import com.justify.service.CompletionListener;
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

    /** Listeners run in order after a justification closes: portal status first, then the webhook. */
    @Bean
    CompletionListener completionListener(JustifyProperties props, ObjectMapper mapper,
                                          FirewallRequestRepository firewallRequests, PlatformTransactionManager txm,
                                          Clock clock) {
        CompletionListener portal = new FirewallStatusUpdater(firewallRequests, new TransactionTemplate(txm), clock);
        CompletionListener webhook = webhookListener(props, mapper);
        return snapshot -> {
            portal.onClosed(snapshot);
            webhook.onClosed(snapshot);
        };
    }

    @Bean
    FirewallRequestService firewallRequestService(JustificationService justifications,
                                                  FirewallRequestRepository repository,
                                                  PlatformTransactionManager txm, Clock clock) {
        return new FirewallRequestService(justifications, repository, new FirewallFormValidator(),
                new TransactionTemplate(txm), clock);
    }

    private static CompletionListener webhookListener(JustifyProperties props, ObjectMapper mapper) {
        JustifyProperties.Webhook w = props.webhook();
        if (!w.enabled()) {
            return CompletionListener.NONE;
        }
        log.info("Webhook enabled: {}", w.url());
        if (w.secret() == null || w.secret().isBlank()) {
            log.warn("WEBHOOK_SECRET is empty: webhook calls are not signed");
        }
        return new WebhookNotifier(java.net.URI.create(w.url()), w.secret(),
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), mapper,
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(),
                java.util.List.of(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30)), w.timeout());
    }

    @Bean
    JustificationService justificationService(EvaluationPipeline pipeline, TemplateRegistry templates,
                                              SubmissionRepository repository, FeedbackRepository feedback,
                                              PlatformTransactionManager txm, ObjectMapper mapper, Clock clock,
                                              CompletionListener listener) {
        return new JustificationService(pipeline, templates, repository, feedback, new TransactionTemplate(txm),
                mapper, clock, listener);
    }

    private static SimpleClientHttpRequestFactory factory(Duration timeout) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(timeout);
        return f;
    }
}
