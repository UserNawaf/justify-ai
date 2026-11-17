package com.justify.core;

import com.justify.testsupport.TestTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageTest {

    @Test
    void parsesTagsAndHeaders() {
        assertThat(Language.parse("ar")).isEqualTo(Language.AR);
        assertThat(Language.parse("ar-SA,ar;q=0.9,en;q=0.8")).isEqualTo(Language.AR);
        assertThat(Language.parse("en-US,ar;q=0.5")).isEqualTo(Language.EN);
        assertThat(Language.parse(null)).isEqualTo(Language.EN);
        assertThat(Language.parse("fr")).isEqualTo(Language.EN);
    }

    @Test
    void arabicFallsBackToEnglish() {
        FieldSpec onlyEnglish = new FieldSpec("a", "A", true, "Q?", "");
        assertThat(onlyEnglish.question(Language.AR)).isEqualTo("Q?");

        FieldSpec duration = TestTemplates.firewall().field("duration").orElseThrow();
        assertThat(duration.question(Language.AR)).contains("دائمة");
        assertThat(duration.question(Language.EN)).contains("permanent");
        assertThat(TestTemplates.firewall().name(Language.AR)).isEqualTo("طلب فتح قاعدة جدار ناري");
    }
}
