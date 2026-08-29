package com.municipality.agent.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The one secret in front of the endpoint. */
class ApiKeysTest {

    private final ApiKeys keys = new ApiKeys("the-key", false);

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
    void withNothingConfiguredTheServiceDoesNotStart() {
        // The alternative is a production service that generates a key, prints it into
        // the log pipeline, and is reachable by everybody who can read a dashboard.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ApiKeys("", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.api.key");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ApiKeys(null, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unlessSomebodyAskedForTheLocalMode() {
        // Which still is not an open endpoint: it is a key nobody has been told, printed
        // where the person who started it can read it.
        var generated = new ApiKeys("", true);

        assertThat(generated.accepts("anything")).isFalse();
        assertThat(generated.accepts("")).isFalse();
    }
}
