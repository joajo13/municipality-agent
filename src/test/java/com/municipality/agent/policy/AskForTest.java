package com.municipality.agent.policy;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asking the resident for something they have not given yet.
 */
class AskForTest {

    @Test
    void refusesToAskForNothing() {
        // An AskFor with an empty list would print a question with no question in it.
        // Whatever produced it meant StartFlow and got the subtraction wrong.
        assertThatThrownBy(() -> new AskFor(Domain.SALUD, Action.START_PROCEDURE, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void keepsItsOwnCopyOfWhatItIsAskingFor() {
        var wanted = new java.util.HashSet<>(Set.of(DNI));
        var askFor = new AskFor(Domain.SALUD, Action.START_PROCEDURE, wanted);

        wanted.clear();

        assertThat(askFor.missing()).containsExactly(DNI);
    }
}
