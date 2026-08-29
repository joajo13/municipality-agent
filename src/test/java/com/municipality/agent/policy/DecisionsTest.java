package com.municipality.agent.policy;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The five outcomes, and what each of them refuses to be built without.
 *
 * <p>A decision travels away from the rules that made it, and is rendered by somebody who
 * was not there. One missing field is a null pointer in a reply to a resident, which is
 * why every one of these is checked at the point it is made.
 */
class DecisionsTest {

    @Test
    void anAnswerIsAboutSomething() {
        assertThatThrownBy(() -> new Answer(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHandoverSaysWhatItIsAbout() {
        // Even when that is UNKNOWN: whoever picks the conversation up needs to know
        // that nobody worked out the topic, which is not the same as not being told.
        assertThatThrownBy(() -> new Handoff(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Handoff(Domain.UNKNOWN).domain()).isEqualTo(Domain.UNKNOWN);
    }

    @Test
    void aProcedureSaysWhichOneAndWithWhat() {
        assertThatThrownBy(() -> new StartFlow(null, Action.START_PROCEDURE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartFlow(Domain.SALUD, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartFlow(Domain.SALUD, Action.START_PROCEDURE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whatAProcedureWasGivenCannotBeChangedUnderIt() {
        var entities = new HashMap<>(Map.of(EntityType.DNI, "20123456"));
        var flow = new StartFlow(Domain.SALUD, Action.START_PROCEDURE, entities);

        entities.clear();

        assertThat(flow.entities()).containsEntry(EntityType.DNI, "20123456");
    }

    @Test
    void askingForNothingIsNotAQuestion() {
        assertThatThrownBy(() -> new AskFor(null, Action.START_PROCEDURE, java.util.Set.of(EntityType.DNI)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AskFor(Domain.SALUD, null, java.util.Set.of(EntityType.DNI)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
