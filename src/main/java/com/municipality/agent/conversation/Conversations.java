package com.municipality.agent.conversation;

import java.util.Optional;

/**
 * Where conversations are kept between turns.
 *
 * <p>An interface with two methods, because that is the whole of what the agent needs
 * and because what is behind it is expected to change: a map while the console runs, a
 * table in production, something else the day one service becomes several. Nothing above
 * this line knows which.
 */
public interface Conversations {

    /** What is remembered about this resident, if anything. */
    Optional<Conversation> of(String userId);

    /**
     * Writes a turn.
     *
     * <p>The write is conditional on {@link Conversation#turns()}: a conversation is
     * saved against the turn count it was read at, and a second write against that same
     * count fails rather than overwriting the first. Two messages from the same resident
     * landing on two instances at once is not a rare event on a messaging channel, and
     * the losing one has to be told, not quietly dropped.
     *
     * @throws ConcurrentTurn when this conversation moved on while the turn was being handled
     */
    Conversation save(Conversation conversation);
}
