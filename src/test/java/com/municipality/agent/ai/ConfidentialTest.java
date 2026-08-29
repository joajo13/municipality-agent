package com.municipality.agent.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What is taken out of a message before it is sent to somebody else's computer.
 *
 * <p>The rule is blunt on purpose. A phone number, a card number and a document number
 * look identical from here, and none of them helps decide whether a message is about
 * licences — so all of them go, and being wrong about which was which stops being a
 * failure that can happen.
 */
class ConfidentialTest {

    @Test
    void aDocumentNumberDoesNotLeave() {
        assertThat(Confidential.withoutIdentifiers("mi dni es 20123456"))
                .isEqualTo("mi dni es [número]");
    }

    @Test
    void neitherDoesOneWrittenInGroups() {
        assertThat(Confidential.withoutIdentifiers("dni 20.123.456")).isEqualTo("dni [número]");
        assertThat(Confidential.withoutIdentifiers("dni 20 123 456")).isEqualTo("dni [número]");
    }

    @Test
    void aClaimNumberLeavesAsWhatItWas() {
        // It says what it is, so saying that is more useful to a classifier than a bare
        // placeholder -- and the digits still do not go.
        assertThat(Confidential.withoutIdentifiers("el REC-2026-00412 sigue igual"))
                .isEqualTo("el [reclamo] sigue igual");
    }

    @Test
    void aPhoneNumberGoesTooEvenThoughNobodyAskedForOne() {
        assertThat(Confidential.withoutIdentifiers("llamame al 3415551234")).isEqualTo("llamame al [número]");
    }

    @Test
    void aStreetNumberStays() {
        // Three digits is a house number and an hour of the day. A complaint with the
        // address taken out of it is harder to route, not safer.
        assertThat(Confidential.withoutIdentifiers("se rompio una luminaria en Sarmiento 450"))
                .isEqualTo("se rompio una luminaria en Sarmiento 450");
    }

    @Test
    void theWordsThemselvesAreLeftAlone() {
        // The model still has to be able to tell what the message is about.
        assertThat(Confidential.withoutIdentifiers("quiero sacar la licencia de conducir"))
                .isEqualTo("quiero sacar la licencia de conducir");
    }

    @Test
    void anEmptyMessageSurvivesTheJourney() {
        assertThat(Confidential.withoutIdentifiers("")).isEmpty();
    }
}
