package com.justify.core;

import com.justify.core.pipeline.EvaluationPipeline;
import com.justify.core.pipeline.EvaluationResult;
import com.justify.testsupport.FakeAiClient;
import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationPipelineTest {

    private static final String FULL = """
            business_purpose: Payroll app must call the bank payment API for monthly salaries
            source: payroll-app-01 (10.10.5.20)
            destination: api.bank.example (203.0.113.10)
            port_protocol: TCP 443
            duration: Permanent
            impact_if_rejected: Salaries cannot be paid on time
            clarity: 9
            """;

    private final Template template = TestTemplates.firewall();
    private final FakeAiClient ai = new FakeAiClient();
    private final EvaluationPipeline pipeline =
            new EvaluationPipeline(ai, new QualityScorer(), new JustificationRenderer(), 3, 3);

    @Test
    void completeTextIsCompleteOnFirstRound() {
        EvaluationResult r = pipeline.evaluateText(template, FULL);

        assertThat(r.status()).isEqualTo(Status.COMPLETE);
        assertThat(r.questions()).isEmpty();
        assertThat(r.missingFields()).isEmpty();
        assertThat(r.completeness()).isEqualTo(1.0);
        assertThat(r.roundNo()).isEqualTo(1);
        assertThat(r.standardizedText()).startsWith("Request type: Firewall rule request")
                .contains("Data classification: Not provided");
    }

    @Test
    void vagueTextAsksAtMostThreeQuestionsInTemplateOrder() {
        EvaluationResult r = pipeline.evaluateText(template, "port_protocol: TCP 443\nplease, urgent");

        assertThat(r.status()).isEqualTo(Status.NEEDS_INFO);
        assertThat(r.missingFields()).containsExactly(
                "business_purpose", "source", "destination", "duration", "impact_if_rejected");
        assertThat(r.questions().keySet()).containsExactly("business_purpose", "source", "destination");
        assertThat(r.standardizedText()).isNull();
    }

    @Test
    void optionalFieldIsNeverAsked() {
        EvaluationResult r = pipeline.evaluateText(template, FULL.replace("duration: Permanent\n", ""));

        assertThat(r.questions()).containsOnlyKeys("duration");
    }

    @Test
    void placeholdersCountAsMissing() {
        EvaluationResult r = pipeline.evaluateText(template,
                FULL.replace("duration: Permanent", "duration: N/A")
                        .replace("TCP 443", "غير محدد"));

        assertThat(r.missingFields()).containsExactly("port_protocol", "duration");
        assertThat(r.fields()).doesNotContainKeys("port_protocol", "duration");
    }

    @Test
    void answersCompleteTheSubmissionAndEarlierValuesAreKept() {
        EvaluationResult first = pipeline.evaluateText(template, FULL.replace("duration: Permanent\n", ""));

        EvaluationResult second = pipeline.applyAnswers(template, first.fields(), first.clarity(),
                Map.of("duration", "Permanent"), 2);

        assertThat(second.status()).isEqualTo(Status.COMPLETE);
        assertThat(second.fields()).containsEntry("source", "payroll-app-01 (10.10.5.20)")
                .containsEntry("duration", "Permanent");
        assertThat(second.roundNo()).isEqualTo(2);
        assertThat(ai.calls()).as("answers are trusted, the model is not called again").isEqualTo(1);
    }

    @Test
    void confirmedAnswersWinOverExtractedValues() {
        EvaluationResult first = pipeline.evaluateText(template, FULL);

        EvaluationResult second = pipeline.applyAnswers(template, first.fields(), first.clarity(),
                Map.of("port_protocol", "TCP 8443"), 2);

        assertThat(second.fields()).containsEntry("port_protocol", "TCP 8443");
    }

    @Test
    void placeholderAnswerDoesNotEraseEarlierValue() {
        EvaluationResult first = pipeline.evaluateText(template, FULL);

        EvaluationResult second = pipeline.applyAnswers(template, first.fields(), first.clarity(),
                Map.of("port_protocol", "unknown"), 2);

        assertThat(second.fields()).containsEntry("port_protocol", "TCP 443");
    }

    @Test
    void lastRoundWithGapsIsIncompleteAndRendered() {
        EvaluationResult r = pipeline.applyAnswers(template, Map.of("port_protocol", "TCP 443"), 5,
                Map.of("source", "10.0.0.1"), 3);

        assertThat(r.status()).isEqualTo(Status.INCOMPLETE);
        assertThat(r.questions()).isEmpty();
        assertThat(r.standardizedText()).contains("Duration: Not provided").contains("Source: 10.0.0.1");
    }

    @Test
    void knownFieldsAreConfirmedAndNeverAsked() {
        EvaluationResult r = pipeline.evaluateText(template,
                "port_protocol: UDP 53\nbusiness_purpose: DNS for the payroll app",
                Map.of("port_protocol", "TCP 443", "source", "app-01 (10.0.0.1)", "destination", "N/A"));

        assertThat(r.fields()).containsEntry("port_protocol", "TCP 443").containsEntry("source", "app-01 (10.0.0.1)")
                .containsEntry("business_purpose", "DNS for the payroll app");
        assertThat(r.missingFields()).containsExactly("destination", "duration", "impact_if_rejected");
    }

    @Test
    void unknownKnownFieldIsRejected() {
        assertThatThrownBy(() -> pipeline.evaluateText(template, "x", Map.of("colour", "blue")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownAnswerKeyIsRejected() {
        assertThatThrownBy(() -> pipeline.applyAnswers(template, Map.of(), 5, Map.of("colour", "blue"), 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("colour");
    }

    @Test
    void blankTextIsRejected() {
        assertThatThrownBy(() -> pipeline.evaluateText(template, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void questionCapIsConfigurable() {
        EvaluationPipeline one = new EvaluationPipeline(ai, new QualityScorer(), new JustificationRenderer(), 1, 3);

        assertThat(one.evaluateText(template, "hello").questions()).hasSize(1);
    }
}
