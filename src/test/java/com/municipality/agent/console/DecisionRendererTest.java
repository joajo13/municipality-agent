package com.municipality.agent.console;

import com.municipality.agent.policy.Answer;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.policy.Handoff;
import com.municipality.agent.policy.StartFlow;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puts a decision into words. This is the only place in the project that writes Spanish:
 * the code and its names are in English, what a resident reads is not.
 *
 * <p>The switch it is built on has no {@code default}, so a sixth kind of decision would
 * stop the build here until somebody says what it sounds like.
 */
class DecisionRendererTest {

    private final DecisionRenderer renderer = new DecisionRenderer();

    // --- what the resident reads ---------------------------------------------

    @Test
    void startingAProcedureSaysWhichOne() {
        var decision = new StartFlow(Domain.RECLAMOS, Action.START_PROCEDURE, Map.of());

        assertThat(renderer.reply(decision)).contains("reclamos");
    }

    @Test
    void askingForSomethingNamesItInSpanish() {
        var decision = new AskFor(Domain.RECLAMOS, Action.CHECK_STATUS, Set.of(CLAIM_NUMBER));

        assertThat(renderer.reply(decision)).contains("número de reclamo");
    }

    @Test
    void askingForTheDniNamesItToo() {
        var decision = new AskFor(Domain.SALUD, Action.START_PROCEDURE, Set.of(DNI));

        assertThat(renderer.reply(decision)).contains("DNI");
    }

    @Test
    void answeringAQuestionSaysWhatItIsAbout() {
        assertThat(renderer.reply(new Answer(Domain.SALUD))).contains("salud");
    }

    @Test
    void aGreetingIsGreetedBackWithoutNamingADomain() {
        // "Te respondo sobre SMALLTALK" is not a thing to say to a person.
        assertThat(renderer.reply(new Answer(Domain.SMALLTALK))).doesNotContain("SMALLTALK");
    }

    @Test
    void theMenuListsWhatThereIsOnOffer() {
        var reply = renderer.reply(new FallbackMenu());

        assertThat(reply).contains("salud").contains("licencias").contains("reclamos");
    }

    @Test
    void aHandoverSaysAPersonIsComing() {
        assertThat(renderer.reply(new Handoff(Domain.RECLAMOS))).contains("persona");
    }

    @Test
    void theMenuAndAHandoverDoNotSoundTheSame() {
        // One means "I did not follow you", the other "I did, and a person takes over".
        assertThat(renderer.reply(new FallbackMenu())).isNotEqualTo(renderer.reply(new Handoff(Domain.SALUD)));
    }

    // --- what the trace shows ------------------------------------------------

    @Test
    void theSummaryNamesTheKindOfDecision() {
        var decision = new StartFlow(Domain.RECLAMOS, Action.START_PROCEDURE, Map.of());

        assertThat(renderer.summary(decision)).contains("StartFlow");
    }

    @Test
    void theSummaryOfAskingShowsWhatIsMissing() {
        var decision = new AskFor(Domain.RECLAMOS, Action.CHECK_STATUS, Set.of(CLAIM_NUMBER));

        assertThat(renderer.summary(decision)).contains("AskFor").contains("CLAIM_NUMBER");
    }
}
