package com.justify.ai;

import com.justify.core.FieldSpec;
import com.justify.core.Template;
import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void listsEveryFieldWithItsRequirement() {
        Template t = TestTemplates.firewall();
        String prompt = builder.systemPrompt(t);

        for (FieldSpec f : t.fields()) {
            assertThat(prompt).contains("- " + f.key() + (f.required() ? " (required)" : " (optional)"));
            assertThat(prompt).contains("\"" + f.key() + "\": string or null");
        }
        assertThat(prompt).contains("Firewall rule request").contains("JSON only").contains("clarity");
    }

    @Test
    void userPromptWrapsTheText() {
        assertThat(builder.userPrompt("  افتح المنفذ 443  ")).contains("\"\"\"\nافتح المنفذ 443\n\"\"\"");
    }
}
