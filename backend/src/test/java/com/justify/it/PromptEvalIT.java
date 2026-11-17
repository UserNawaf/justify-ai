package com.justify.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justify.ai.ExtractionParser;
import com.justify.ai.GroqAiClient;
import com.justify.ai.OllamaAiClient;
import com.justify.ai.PromptBuilder;
import com.justify.core.AiUnavailableException;
import com.justify.core.Extraction;
import com.justify.core.FieldSpec;
import com.justify.core.Placeholders;
import com.justify.core.Template;
import com.justify.core.pipeline.AiClient;
import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the golden set through the real model (docs/TESTING.md, section 3). Manual:
 * {@code RUN_AI_EVAL=true mvn verify -Dit.test=PromptEvalIT}. Provider from AI_PROVIDER (ollama | groq).
 */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVAL", matches = "true")
class PromptEvalIT {

    record Case(String id, String lang, boolean clear, String text, List<String> present) {
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static AiClient client(ObjectMapper mapper) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(Duration.ofSeconds(180));
        PromptBuilder prompts = new PromptBuilder();
        ExtractionParser parser = new ExtractionParser(mapper);
        if (env("AI_PROVIDER", "ollama").toLowerCase(Locale.ROOT).equals("groq")) {
            RestClient http = RestClient.builder().baseUrl(env("GROQ_URL", "https://api.groq.com/openai/v1"))
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + env("GROQ_API_KEY", ""))
                    .requestFactory(f).build();
            return new GroqAiClient(http, env("GROQ_MODEL", "llama-3.3-70b-versatile"), prompts, parser, 2);
        }
        RestClient http = RestClient.builder().baseUrl(env("OLLAMA_URL", "http://localhost:11434"))
                .requestFactory(f).build();
        return new OllamaAiClient(http, env("OLLAMA_MODEL", "llama3.1:8b"), prompts, parser, 2);
    }

    @Test
    void goldenSet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Case> cases;
        try (InputStream in = getClass().getResourceAsStream("/eval/golden-firewall.json")) {
            cases = mapper.readValue(in, new TypeReference<>() {
            });
        }
        Template template = TestTemplates.firewall();
        AiClient ai = client(mapper);
        double minAccuracy = Double.parseDouble(System.getProperty("eval.minAccuracy", "0.75"));

        int unusable = 0;
        int correct = 0;
        int judged = 0;
        List<Integer> clearScores = new ArrayList<>();
        List<Integer> vagueScores = new ArrayList<>();
        StringBuilder report = new StringBuilder("\n=== Prompt evaluation ===\n");
        long started = System.nanoTime();

        for (Case c : cases) {
            Extraction e;
            try {
                e = ai.extract(template, c.text());
            } catch (AiUnavailableException ex) {
                unusable++;
                report.append(String.format("%-24s UNUSABLE%n", c.id()));
                continue;
            }
            int caseCorrect = 0;
            List<String> wrong = new ArrayList<>();
            for (FieldSpec f : template.requiredFields()) {
                boolean expected = c.present().contains(f.key());
                boolean actual = !Placeholders.isMissing(e.fields().get(f.key()));
                judged++;
                if (expected == actual) {
                    correct++;
                    caseCorrect++;
                } else {
                    wrong.add(f.key() + (expected ? " (missed)" : " (invented)"));
                }
            }
            (c.clear() ? clearScores : vagueScores).add(e.clarity());
            report.append(String.format("%-24s %-5s clarity=%2d fields=%d/%d %s%n", c.id(), c.lang(), e.clarity(),
                    caseCorrect, template.requiredFields().size(), wrong.isEmpty() ? "" : "wrong: " + wrong));
        }

        double accuracy = judged == 0 ? 0 : (double) correct / judged;
        double clearAvg = clearScores.stream().mapToInt(i -> i).average().orElse(0);
        double vagueAvg = vagueScores.stream().mapToInt(i -> i).average().orElse(0);
        report.append(String.format("unusable replies: %d%nfield accuracy:   %.1f%% (min %.0f%%)%n"
                        + "clarity clear/vague: %.1f / %.1f%nseconds: %.1f%n", unusable, accuracy * 100,
                minAccuracy * 100, clearAvg, vagueAvg, (System.nanoTime() - started) / 1e9));
        System.out.println(report);

        assertThat(unusable).as("unusable replies").isZero();
        assertThat(accuracy).as("field accuracy").isGreaterThanOrEqualTo(minAccuracy);
        assertThat(clearAvg).as("clear texts must score higher than vague ones").isGreaterThan(vagueAvg);
    }
}
