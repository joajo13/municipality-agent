package com.municipality.agent.conversation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The store that runs when no database is configured.
 *
 * <p>These are the tests every store has to pass, which is why they are written against
 * the interface and not against the map. The conditional write is the interesting one:
 * a stand-in that always succeeds would hide exactly the bug that only ever shows up
 * with two instances running.
 */
class InMemoryConversationsTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    private final Conversations conversations = new InMemoryConversations();

    @Test
    void aResidentNobodyHasHeardFromIsNotRemembered() {
        assertThat(conversations.of("user-1")).isEmpty();
    }

    @Test
    void whatWasWrittenComesBack() {
        var first = Conversation.startedBy("user-1", NOON).after(null, NOON);

        conversations.save(first);

        assertThat(conversations.of("user-1")).contains(first);
    }

    @Test
    void oneTurnFollowsAnother() {
        var first = Conversation.startedBy("user-1", NOON).after(null, NOON);
        var second = first.after(null, NOON);

        conversations.save(first);
        conversations.save(second);

        assertThat(conversations.of("user-1")).contains(second);
    }

    @Test
    void aSecondWriteOfTheSameTurnLoses() {
        var first = Conversation.startedBy("user-1", NOON).after(null, NOON);

        conversations.save(first);

        assertThatThrownBy(() -> conversations.save(first))
                .isInstanceOf(ConcurrentTurn.class)
                .hasMessageContaining("user-1");
    }

    @Test
    void aTurnBuiltOnSomethingThatWasNeverThereLoses() {
        // Turn 5 of a conversation this store has never seen: something else wrote it,
        // or it expired underneath. Either way this is not the next turn of anything.
        var outOfNowhere = new Conversation("user-1", java.util.Map.of(), null, 5, NOON);

        assertThatThrownBy(() -> conversations.save(outOfNowhere)).isInstanceOf(ConcurrentTurn.class);
    }

    @Test
    void residentsAreKeptApart() {
        conversations.save(Conversation.startedBy("user-1", NOON).after(null, NOON));

        assertThat(conversations.of("user-2")).isEmpty();
    }
}
