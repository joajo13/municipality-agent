package com.municipality.agent.conversation;

/**
 * Two turns of the same conversation were handled at once, and this one lost.
 *
 * <p>Nothing was written. Whoever is holding this has a choice to make — handle the
 * message again against what is now stored, or hand it back to the channel to be
 * redelivered — and it is a choice, which is why this is not swallowed here.
 */
public class ConcurrentTurn extends RuntimeException {

    public ConcurrentTurn(String userId) {
        super("Conversation with " + userId + " moved on while this turn was being handled");
    }
}
