package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stand-in classifier, until a real model arrives in step 6. It looks for words it
 * knows and nothing else — no context, no history, no reading between the lines.
 *
 * <p>Ambiguity is deliberately not its problem. "quiero un turno y consultar mi reclamo"
 * lands on one domain and that is fine: the point of this class is to let the rest of
 * the pipeline run end to end without a network call, not to be right.
 */
class KeywordClassifierTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-20T10:00:00Z");

    private final Classifier classifier = new KeywordClassifier();

    /** Classifies a line as if a resident had typed it. */
    private Intent classify(String text) {
        return classifier.classify(new NormalizedMessage("trace-1", "user-1", SENT_AT, text));
    }

    // --- which domain --------------------------------------------------------

    @Test
    void recognisesSalud() {
        assertThat(classify("necesito un turno en el hospital").domain()).isEqualTo(Domain.SALUD);
    }

    @Test
    void recognisesLicencias() {
        assertThat(classify("quiero sacar la licencia de conducir").domain()).isEqualTo(Domain.LICENCIAS);
    }

    @Test
    void recognisesReclamos() {
        assertThat(classify("se rompio una luminaria en Sarmiento 450").domain()).isEqualTo(Domain.RECLAMOS);
    }

    @Test
    void recognisesSmalltalk() {
        assertThat(classify("hola, buenas tardes").domain()).isEqualTo(Domain.SMALLTALK);
    }

    @Test
    void anythingItDoesNotRecogniseIsUnknown() {
        assertThat(classify("cuanto sale el dolar hoy").domain()).isEqualTo(Domain.UNKNOWN);
    }

    // --- how it matches ------------------------------------------------------

    @Test
    void matchingIgnoresCase() {
        assertThat(classify("QUIERO LA LICENCIA").domain()).isEqualTo(Domain.LICENCIAS);
    }

    @Test
    void matchingIgnoresAccents() {
        // People type "medico" and "médico" interchangeably, and so do their phones.
        assertThat(classify("necesito ver a un médico").domain()).isEqualTo(Domain.SALUD);
    }

    @Test
    void matchingIsByWholeWordSoSaludosIsAGreetingAndNotHealthcare() {
        // "saludos" contains "salud". Matching on substrings would route a goodbye
        // into the health domain.
        assertThat(classify("saludos, muchas gracias").domain()).isEqualTo(Domain.SMALLTALK);
    }

    @Test
    void aRealDomainWinsOverAGreeting() {
        // Almost every message opens with "hola". It must not swallow the actual request.
        assertThat(classify("hola, quiero hacer un reclamo").domain()).isEqualTo(Domain.RECLAMOS);
    }

    @Test
    void whenTwoDomainsMatchTheFirstOneDeclaredWins() {
        // Not a judgement about which is more important -- just a rule, so the result
        // is predictable. Sorting this out properly is the real classifier's job.
        var text = "quiero un turno en el hospital y consultar mi reclamo";

        assertThat(classify(text).domain()).isEqualTo(Domain.SALUD);
    }

    // --- which action --------------------------------------------------------

    @Test
    void askingHowSomethingIsGoingIsCheckingItsStatus() {
        var intent = classify("quiero consultar el estado de mi reclamo");

        assertThat(intent.action()).isEqualTo(Action.CHECK_STATUS);
    }

    @Test
    void askingForAPersonIsAHandoff() {
        assertThat(classify("quiero hablar con una persona").action()).isEqualTo(Action.HANDOFF);
    }

    @Test
    void askingWhatOrWhenIsAskingForInformation() {
        var intent = classify("cuales son los requisitos para la licencia");

        assertThat(intent.action()).isEqualTo(Action.INFORMATION);
    }

    @Test
    void wantingSomethingDoneStartsAProcedure() {
        // Nothing said otherwise, and a domain matched: they want the thing done.
        assertThat(classify("quiero sacar la licencia").action()).isEqualTo(Action.START_PROCEDURE);
    }

    @Test
    void aGreetingAsksForNothingSoItCountsAsInformation() {
        // There is no procedure to start for "hola". Answering is all there is to do.
        assertThat(classify("hola, buenas").action()).isEqualTo(Action.INFORMATION);
    }

    @Test
    void aHandoffIsRecognisedEvenWhenNoDomainIs() {
        // It does not know what they want to talk about, but it does know they want
        // to talk to somebody.
        var intent = classify("quiero hablar con una persona");

        assertThat(intent.domain()).isEqualTo(Domain.UNKNOWN);
        assertThat(intent.action()).isEqualTo(Action.HANDOFF);
    }

    // --- how sure it is ------------------------------------------------------

    @Test
    void seeingAWordItKnowsIsAsSureAsItGets() {
        // Not a probability. It either saw the word or it did not.
        assertThat(classify("quiero sacar la licencia").confidence()).isEqualTo(1.0);
    }

    @Test
    void recognisingNothingAtAllIsNoConfidenceAtAll() {
        // Not a single word it knows -- not even one that gives away the action.
        assertThat(classify("esta lloviendo muchisimo").confidence()).isEqualTo(0.0);
    }

    @Test
    void recognisingOnlyTheActionStillCounts() {
        // No domain word, but "persona" was understood. That is not zero knowledge.
        assertThat(classify("quiero hablar con una persona").confidence()).isEqualTo(1.0);
    }
}
