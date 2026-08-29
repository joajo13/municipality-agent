package com.municipality.agent.conversation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What every store has to do, written once.
 *
 * <p>There are two implementations and they have nothing in common — a map and a table —
 * which is exactly why the promises they make have to be the same ones. A store that is
 * only exercised through the map is a store that has never been asked the questions the
 * table will be asked in production.
 *
 * <p>The conditional write is the reason this class exists. A stand-in that always
 * succeeds hides the one failure that only ever happens with two instances running, and
 * by the time it shows up it is a resident's answer that was overwritten.
 */
public abstract class ConversationsContract {

    protected static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    protected abstract Conversations conversations();

    private static Conversation firstTurnOf(String userId) {
        return Conversation.startedBy(userId, NOON).after(null, NOON);
    }

    @Test
    void aResidentNobodyHasHeardFromIsNotRemembered() {
        assertThat(conversations().of("user-1")).isEmpty();
    }

    @Test
    void whatWasWrittenComesBack() {
        conversations().save(firstTurnOf("user-1"));

        assertThat(conversations().of("user-1")).contains(firstTurnOf("user-1"));
    }

    @Test
    void whatTheResidentGaveComesBackWithIt() {
        conversations().save(Conversation.startedBy("user-1", NOON)
                .learned(Map.of(DNI, "20123456", CLAIM_NUMBER, "REC-2026-00412"))
                .after(null, NOON));

        assertThat(conversations().of("user-1"))
                .get()
                .extracting(Conversation::known)
                .isEqualTo(Map.of(DNI, "20123456", CLAIM_NUMBER, "REC-2026-00412"));
    }

    @Test
    void theQuestionOnTheTableComesBackWithItToo() {
        var asked = new OpenQuestion(
                new com.municipality.agent.router.Intent(
                        com.municipality.agent.router.Domain.RECLAMOS,
                        com.municipality.agent.router.Action.CHECK_STATUS,
                        0.85),
                Set.of(CLAIM_NUMBER));

        conversations().save(Conversation.startedBy("user-1", NOON).after(asked, NOON));

        assertThat(conversations().of("user-1")).get().extracting(Conversation::asked).isEqualTo(asked);
    }

    @Test
    void aConversationThatIsWaitingForNothingSaysSo() {
        conversations().save(firstTurnOf("user-1"));

        assertThat(conversations().of("user-1")).get().extracting(Conversation::asked).isNull();
    }

    @Test
    void oneTurnFollowsAnother() {
        var first = firstTurnOf("user-1");
        var second = first.learned(Map.of(DNI, "20123456")).after(null, NOON);

        conversations().save(first);
        conversations().save(second);

        assertThat(conversations().of("user-1")).contains(second);
    }

    @Test
    void whatIsForgottenStaysForgotten() {
        var first = firstTurnOf("user-1").learned(Map.of(DNI, "20123456"));

        conversations().save(first);
        conversations().save(first.forgotten(NOON).after(null, NOON));

        assertThat(conversations().of("user-1")).get().extracting(Conversation::known).isEqualTo(Map.of());
    }

    @Test
    void aSecondWriteOfTheSameTurnLoses() {
        var first = firstTurnOf("user-1");

        conversations().save(first);

        assertThatThrownBy(() -> conversations().save(first))
                .isInstanceOf(ConcurrentTurn.class)
                .hasMessageContaining("user-1");
    }

    @Test
    void aTurnBuiltOnSomethingThatWasNeverThereLoses() {
        // Turn 5 of a conversation this store has never seen: something else wrote it, or
        // it was swept underneath. Either way this is not the next turn of anything.
        var outOfNowhere = new Conversation("user-1", Map.of(), null, 5, NOON);

        assertThatThrownBy(() -> conversations().save(outOfNowhere)).isInstanceOf(ConcurrentTurn.class);
    }

    @Test
    void aTurnBuiltOnAConversationThatMovedOnLoses() {
        var first = firstTurnOf("user-1");
        var second = first.after(null, NOON);

        conversations().save(first);
        conversations().save(second);

        assertThatThrownBy(() -> conversations().save(second)).isInstanceOf(ConcurrentTurn.class);
    }

    @Test
    void residentsAreKeptApart() {
        conversations().save(firstTurnOf("user-1"));

        assertThat(conversations().of("user-2")).isEmpty();
    }
}
