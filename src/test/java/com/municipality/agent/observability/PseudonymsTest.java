package com.municipality.agent.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The name a resident is written down under.
 *
 * <p>The phone number is what this agent knows somebody by, and it is the last thing that
 * should end up in a log aggregator. What support actually needs is far less: a stable
 * way to say "the same person as the line above".
 */
class PseudonymsTest {

    private static final String PHONE = "+5493415551234";

    private final Pseudonyms pseudonyms = new Pseudonyms("a-secret");

    @Test
    void theSamePersonReadsTheSameWay() {
        assertThat(pseudonyms.of(PHONE)).isEqualTo(pseudonyms.of(PHONE));
    }

    @Test
    void differentPeopleReadDifferently() {
        assertThat(pseudonyms.of(PHONE)).isNotEqualTo(pseudonyms.of("+5493415559999"));
    }

    @Test
    void thePhoneNumberIsNotInThere() {
        assertThat(pseudonyms.of(PHONE)).doesNotContain("5551234").doesNotContain(PHONE);
    }

    @Test
    void aNameIsShortEnoughToReadInALogLine() {
        assertThat(pseudonyms.of(PHONE)).hasSize(12).matches("[0-9a-f]+");
    }

    @Test
    void adifferentSecretIsADifferentSetOfNames() {
        // Which is the point of the secret. Without one, a phone number is a small enough
        // space that a digest of it is the number with extra steps.
        assertThat(new Pseudonyms("another-secret").of(PHONE)).isNotEqualTo(pseudonyms.of(PHONE));
    }

    @Test
    void withNoSecretConfiguredTheNamesStillHoldWithinTheRun() {
        var generated = new Pseudonyms("");

        assertThat(generated.of(PHONE)).isEqualTo(generated.of(PHONE));
    }

    @Test
    void andAreNotTheSameNamesAsAnotherRun() {
        assertThat(new Pseudonyms(null).of(PHONE)).isNotEqualTo(new Pseudonyms(null).of(PHONE));
    }
}
