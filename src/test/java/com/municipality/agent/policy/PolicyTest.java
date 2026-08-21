package com.municipality.agent.policy;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turns what the resident meant into what the agent does about it.
 *
 * <p>This is the deterministic half of the system. A model works out the intent; from
 * there on it is plain Java, so the rules a municipality actually has — which data a
 * procedure needs, when to give up and offer a menu, when to put a person on — are
 * testable and always applied the same way.
 *
 * <p>Nothing here executes anything. Every method returns an object saying what ought
 * to happen; who carries it out is somebody else's problem.
 */
class PolicyTest {

    private final Policy policy = new Policy();

    private Decision decide(Domain domain, Action action) {
        return decide(domain, action, 1.0, Map.of());
    }

    private Decision decide(Domain domain, Action action, Map<EntityType, String> known) {
        return decide(domain, action, 1.0, known);
    }

    private Decision decide(Domain domain, Action action, double confidence, Map<EntityType, String> known) {
        return policy.decide(new Intent(domain, action, confidence), known);
    }

    // --- everything it needs is there ----------------------------------------

    @Test
    void filingAReclamoNeedsNothingSoItStartsRightAway() {
        assertThat(decide(Domain.RECLAMOS, Action.START_PROCEDURE)).isInstanceOf(StartFlow.class);
    }

    @Test
    void checkingAReclamoWithItsNumberStarts() {
        var known = Map.of(CLAIM_NUMBER, "917435");

        assertThat(decide(Domain.RECLAMOS, Action.CHECK_STATUS, known)).isInstanceOf(StartFlow.class);
    }

    @Test
    void bookingAtSaludWithTheDniStarts() {
        var known = Map.of(DNI, "30111222");

        assertThat(decide(Domain.SALUD, Action.START_PROCEDURE, known)).isInstanceOf(StartFlow.class);
    }

    @Test
    void whatWasKnownTravelsWithTheDecision() {
        // Whoever runs the procedure needs the data, not just permission to run it.
        var known = Map.of(DNI, "30111222");

        var decision = decide(Domain.SALUD, Action.START_PROCEDURE, known);

        assertThat(decision).isInstanceOfSatisfying(StartFlow.class,
                flow -> assertThat(flow.entities()).containsEntry(DNI, "30111222"));
    }

    // --- something is missing ------------------------------------------------

    @Test
    void checkingAReclamoWithoutItsNumberAsksForIt() {
        var decision = decide(Domain.RECLAMOS, Action.CHECK_STATUS);

        assertThat(decision).isInstanceOfSatisfying(AskFor.class,
                askFor -> assertThat(askFor.missing()).containsExactly(CLAIM_NUMBER));
    }

    @Test
    void bookingAtSaludWithoutTheDniAsksForIt() {
        var decision = decide(Domain.SALUD, Action.START_PROCEDURE);

        assertThat(decision).isInstanceOfSatisfying(AskFor.class,
                askFor -> assertThat(askFor.missing()).containsExactly(DNI));
    }

    @Test
    void itOnlyAsksForWhatIsActuallyStillMissing() {
        // The dni is already known, so it must not be asked for again.
        var known = Map.of(DNI, "30111222");

        assertThat(decide(Domain.LICENCIAS, Action.START_PROCEDURE, known)).isInstanceOf(StartFlow.class);
    }

    @Test
    void dataThatThisProcedureDoesNotNeedIsIgnored() {
        // Knowing the claim number does not help book a licence appointment.
        var known = Map.of(CLAIM_NUMBER, "917435");

        var decision = decide(Domain.LICENCIAS, Action.START_PROCEDURE, known);

        assertThat(decision).isInstanceOfSatisfying(AskFor.class,
                askFor -> assertThat(askFor.missing()).containsExactly(DNI));
    }

    // --- nothing to do but reply ---------------------------------------------

    @Test
    void aQuestionIsAnswered() {
        assertThat(decide(Domain.SALUD, Action.INFORMATION)).isInstanceOf(Answer.class);
    }

    @Test
    void aGreetingIsAnswered() {
        assertThat(decide(Domain.SMALLTALK, Action.INFORMATION)).isInstanceOf(Answer.class);
    }

    @Test
    void anAnswerRemembersWhatItIsAbout() {
        var decision = decide(Domain.SALUD, Action.INFORMATION);

        assertThat(decision).isInstanceOfSatisfying(Answer.class,
                answer -> assertThat(answer.domain()).isEqualTo(Domain.SALUD));
    }

    // --- it could not work out what they wanted ------------------------------

    @Test
    void anUnrecognisedTopicGetsTheMenu() {
        assertThat(decide(Domain.UNKNOWN, Action.INFORMATION)).isInstanceOf(FallbackMenu.class);
    }

    @Test
    void beingUnsureGetsTheMenuEvenWhenTheTopicIsRecognised() {
        // Half-understanding a request is worse than admitting it and offering options.
        var decision = decide(Domain.RECLAMOS, Action.START_PROCEDURE, 0.3, Map.of());

        assertThat(decision).isInstanceOf(FallbackMenu.class);
    }

    // --- put a person on -----------------------------------------------------

    @Test
    void askingForAPersonHandsOver() {
        assertThat(decide(Domain.SALUD, Action.HANDOFF)).isInstanceOf(Handoff.class);
    }

    @Test
    void aHandoffCarriesTheTopicSoThePersonKnowsWhatItIsAbout() {
        var decision = decide(Domain.RECLAMOS, Action.HANDOFF);

        assertThat(decision).isInstanceOfSatisfying(Handoff.class,
                handoff -> assertThat(handoff.domain()).isEqualTo(Domain.RECLAMOS));
    }

    @Test
    void handingOverWinsOverNotBeingSure() {
        // Somebody who asked for a human gets one. Offering them a menu instead is the
        // single most annoying thing this agent could do.
        var decision = decide(Domain.UNKNOWN, Action.HANDOFF, 0.2, Map.of());

        assertThat(decision).isInstanceOf(Handoff.class);
    }
}
