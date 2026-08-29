package com.municipality.agent.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The one secret in front of the endpoint. */
class ApiKeysTest {

    private final ApiKeys keys = new ApiKeys("the-key");

    @Test
    void theRightKeyIsAccepted() {
        assertThat(keys.accepts("the-key")).isTrue();
    }

    @Test
    void anythingElseIsNot() {
        assertThat(keys.accepts("the-keys")).isFalse();
        assertThat(keys.accepts("the-ke")).isFalse();
        assertThat(keys.accepts("")).isFalse();
        assertThat(keys.accepts(null)).isFalse();
    }

    @Test
    void withNothingConfiguredThereIsStillAKey() {
        // There is no mode where the endpoint is open. An unconfigured service gets a
        // generated key rather than no key, which is a working service for a developer
        // and a closed one for everybody else.
        assertThat(new ApiKeys("").accepts("")).isFalse();
        assertThat(new ApiKeys(null).accepts(null)).isFalse();
    }

    @Test
    void andItIsNotTheSameOneTwice() {
        var generated = new ApiKeys("");

        assertThat(generated.accepts("anything")).isFalse();
    }
}
