package com.municipality.agent.conversation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conversations in a map. What the console runs on, and what a test runs on when the
 * subject is not the storage.
 *
 * <p>It enforces the same conditional write as the real store rather than being a
 * simpler thing that always succeeds. A stand-in that cannot fail the way production
 * fails is a stand-in that hides the bug.
 */
public class InMemoryConversations implements Conversations {

    private final Map<String, Conversation> stored = new ConcurrentHashMap<>();

    @Override
    public Optional<Conversation> of(String userId) {
        return Optional.ofNullable(stored.get(userId));
    }

    @Override
    public Conversation save(Conversation conversation) {
        String userId = conversation.userId();

        var lost = new AtomicBoolean();

        // One atomic step: read what is there, check it is what this turn was built on,
        // and write. Anything less and two turns can both pass the check.
        stored.compute(userId, (id, current) -> {
            int expected = conversation.turns() - 1;

            if (current == null ? expected != 0 : current.turns() != expected) {
                lost.set(true);
                return current;
            }

            return conversation;
        });

        if (lost.get()) throw new ConcurrentTurn(userId);

        return conversation;
    }
}
