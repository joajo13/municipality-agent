package com.municipality.agent.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the classifier concluded: the topic, what to do about it, and how sure it is.
 *
 * <p>The confidence is the field worth guarding. Whoever fills it next is a language
 * model, and a number outside 0..1 arriving from one would otherwise travel silently
 * into whatever threshold reads it.
 */
class IntentTest {

    @Test
    void rejectsConfidenceAboveOne() {
        assertThatThrownBy(() -> new Intent(Domain.SALUD, Action.START_PROCEDURE, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejectsNegativeConfidence() {
        assertThatThrownBy(() -> new Intent(Domain.SALUD, Action.START_PROCEDURE, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void acceptsBothEndsOfTheRange() {
        assertThat(new Intent(Domain.SALUD, Action.START_PROCEDURE, 0.0).confidence()).isEqualTo(0.0);
        assertThat(new Intent(Domain.SALUD, Action.START_PROCEDURE, 1.0).confidence()).isEqualTo(1.0);
    }

    @Test
    void rejectsAMissingDomain() {
        assertThatThrownBy(() -> new Intent(null, Action.START_PROCEDURE, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domain");
    }

    @Test
    void rejectsAMissingAction() {
        assertThatThrownBy(() -> new Intent(Domain.SALUD, null, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }
}
