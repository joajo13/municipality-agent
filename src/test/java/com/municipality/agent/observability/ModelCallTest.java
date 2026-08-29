package com.municipality.agent.observability;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What one call to a model was, and what it cannot be. */
class ModelCallTest {

    @Test
    void tokensAddUp() {
        assertThat(new ModelCall("m", 412, 18, Duration.ofMillis(1)).totalTokens()).isEqualTo(430);
    }

    @Test
    void aCallWithoutAModelIsNotACall() {
        assertThatThrownBy(() -> new ModelCall(" ", 1, 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokensCannotBeSpentBackwards() {
        assertThatThrownBy(() -> new ModelCall("m", -1, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCall("m", 0, -1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neitherCanTime() {
        assertThatThrownBy(() -> new ModelCall("m", 0, 0, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelCall("m", 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
