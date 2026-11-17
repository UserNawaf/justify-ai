package com.justify.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityScorerTest {

    private final QualityScorer scorer = new QualityScorer();

    @Test
    void weightsAreSixtyFortyOnTenPointScale() {
        assertThat(scorer.score(1.0, 10)).isEqualTo(10.0);
        assertThat(scorer.score(1.0, 0)).isEqualTo(6.0);
        assertThat(scorer.score(0.0, 10)).isEqualTo(4.0);
        assertThat(scorer.score(0.5, 5)).isEqualTo(5.0);
    }

    @Test
    void roundsToOneDecimal() {
        assertThat(scorer.score(4.0 / 6, 7)).isEqualTo(6.8);
    }

    @Test
    void clampsOutOfRangeInputs() {
        assertThat(scorer.score(2.0, 50)).isEqualTo(10.0);
        assertThat(scorer.score(-1.0, -5)).isEqualTo(0.0);
    }
}
