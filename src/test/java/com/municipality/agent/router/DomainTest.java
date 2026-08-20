package com.municipality.agent.router;

import org.junit.jupiter.api.Test;

import static com.municipality.agent.router.Action.CHECK_STATUS;
import static com.municipality.agent.router.Action.HANDOFF;
import static com.municipality.agent.router.Action.INFORMATION;
import static com.municipality.agent.router.Action.START_PROCEDURE;
import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a domain needs from the resident before it can help them. This is the list the
 * next step reads to decide whether to act or to ask for something first.
 *
 * <p>Knowing the domain is not enough to answer that: the same domain needs different
 * things depending on what the resident wants done. Hence {@code requires(Action)}
 * rather than a fixed set per domain.
 */
class DomainTest {

    @Test
    void bookingSomethingAtSaludNeedsTheResidentsDni() {
        assertThat(Domain.SALUD.requires(START_PROCEDURE)).containsExactly(DNI);
    }

    @Test
    void licenciasAlsoIdentifiesTheResidentByDni() {
        assertThat(Domain.LICENCIAS.requires(START_PROCEDURE)).containsExactly(DNI);
    }

    @Test
    void checkingAReclamoNeedsItsNumber() {
        assertThat(Domain.RECLAMOS.requires(CHECK_STATUS)).containsExactly(CLAIM_NUMBER);
    }

    @Test
    void openingAReclamoNeedsNoNumberBecauseItDoesNotExistYet() {
        // The case that motivated requires(Action): someone reporting a broken streetlight
        // cannot be asked for the claim number they are about to be given.
        assertThat(Domain.RECLAMOS.requires(START_PROCEDURE)).isEmpty();
    }

    @Test
    void answeringAQuestionNeverNeedsIdentity() {
        // Opening hours are public. Nobody has to identify themselves to be told them.
        assertThat(Domain.SALUD.requires(INFORMATION)).isEmpty();
        assertThat(Domain.LICENCIAS.requires(INFORMATION)).isEmpty();
        assertThat(Domain.RECLAMOS.requires(INFORMATION)).isEmpty();
    }

    @Test
    void handingOverToAPersonNeverNeedsIdentity() {
        // Whatever is missing is the human's problem now, not the agent's.
        assertThat(Domain.SALUD.requires(HANDOFF)).isEmpty();
        assertThat(Domain.RECLAMOS.requires(HANDOFF)).isEmpty();
    }

    @Test
    void smalltalkNeedsNothing() {
        assertThat(Domain.SMALLTALK.requires(START_PROCEDURE)).isEmpty();
        assertThat(Domain.SMALLTALK.requires(INFORMATION)).isEmpty();
    }

    @Test
    void outOfDomainNeedsNothing() {
        assertThat(Domain.OUT_OF_DOMAIN.requires(START_PROCEDURE)).isEmpty();
        assertThat(Domain.OUT_OF_DOMAIN.requires(INFORMATION)).isEmpty();
    }

    @Test
    void everyDomainAnswersForEveryAction() {
        // No domain and action pair may blow up: the next step asks this for whatever
        // the classifier produced, without checking the combination first.
        for (Domain domain : Domain.values()) {
            for (Action action : Action.values()) {
                assertThat(domain.requires(action)).isNotNull();
            }
        }
    }
}
