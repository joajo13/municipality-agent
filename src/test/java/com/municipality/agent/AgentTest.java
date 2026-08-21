package com.municipality.agent;

import com.municipality.agent.message.Image;
import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.message.NoMediaDescriber;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.message.Text;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.policy.Handoff;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.policy.StartFlow;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.KeywordClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three stages joined up: what arrived becomes text, the text becomes an intent, and
 * the intent becomes a decision.
 *
 * <p>Real collaborators throughout, no stand-ins beyond the ones that are the real thing
 * for now — nothing here reaches the network, so there is nothing to fake.
 */
class AgentTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-21T10:00:00Z");

    private final Agent agent = new Agent(new Normalizer(new NoMediaDescriber()), new KeywordClassifier(), new Policy());

    private Outcome handle(String typed) {
        var incoming = new IncomingMessage("trace-1", "user-1", SENT_AT, List.of(new Text(typed)));
        return agent.handle(incoming);
    }

    @Test
    void reportingSomethingBrokenStartsAComplaint() {
        var outcome = handle("se rompio una luminaria en Sarmiento 450");

        assertThat(outcome.intent().domain()).isEqualTo(Domain.RECLAMOS);
        assertThat(outcome.intent().action()).isEqualTo(Action.START_PROCEDURE);
        assertThat(outcome.decision()).isInstanceOf(StartFlow.class);
    }

    @Test
    void askingAfterAComplaintAsksForItsNumber() {
        var outcome = handle("quiero consultar el estado de mi reclamo");

        assertThat(outcome.decision()).isInstanceOfSatisfying(AskFor.class,
                askFor -> assertThat(askFor.missing()).containsExactly(CLAIM_NUMBER));
    }

    @Test
    void somethingItCannotPlaceGetsTheMenu() {
        assertThat(handle("esta lloviendo muchisimo").decision()).isInstanceOf(FallbackMenu.class);
    }

    @Test
    void askingForAPersonHandsOver() {
        assertThat(handle("quiero hablar con una persona").decision()).isInstanceOf(Handoff.class);
    }

    @Test
    void theOutcomeKeepsTheTextThatWasClassified() {
        // The console shows this, and it is not always what the resident typed: media is
        // announced, whitespace goes, long messages are cut.
        var outcome = handle("   QUIERO LA LICENCIA   ");

        assertThat(outcome.message().text()).isEqualTo("QUIERO LA LICENCIA");
    }

    @Test
    void aPhotoNobodyHasLookedAtDoesNotRouteAnywhereByAccident() {
        // Once it reaches the classifier a placeholder is a word like any other. The day
        // "imagen" or "documento" becomes a keyword, sending a photo would quietly route
        // somebody into a procedure they never asked for.
        var photo = new Image("https://cdn.example/img/1.jpg", null);
        var incoming = new IncomingMessage("trace-1", "user-1", SENT_AT, List.of(photo));

        var outcome = agent.handle(incoming);

        assertThat(outcome.message().text()).isEqualTo("[imagen]");
        assertThat(outcome.intent().domain()).isEqualTo(Domain.UNKNOWN);
    }

    @Test
    void theTraceIdSurvivesTheWholePipeline() {
        // One conversation has to be followable across logs from end to end.
        var incoming = new IncomingMessage("trace-9", "user-9", SENT_AT, List.of(new Text("hola")));

        assertThat(agent.handle(incoming).message().traceId()).isEqualTo("trace-9");
    }
}
