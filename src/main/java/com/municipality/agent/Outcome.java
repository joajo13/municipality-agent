package com.municipality.agent;

import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.policy.Decision;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;

import java.util.Map;

/**
 * Everything one trip through the agent produced, not just the answer.
 *
 * <p>The decision alone would be enough to reply. The rest is here because a decision
 * that looks right for the wrong reason is indistinguishable from one that is right, and
 * telling those apart is what the console, the logs and the golden transcripts are all
 * for.
 *
 * @param message      what was actually classified, which is not always what was typed
 * @param intent       what the resident was taken to mean, after the conversation had its say
 * @param decision     what the agent is going to do about it
 * @param given        what this message handed over, as opposed to what was already known
 * @param conversation what the agent remembers now that this turn has been written
 */
public record Outcome(
        NormalizedMessage message,
        Intent intent,
        Decision decision,
        Map<EntityType, String> given,
        Conversation conversation) {

    public Outcome {
        if (message == null) throw new IllegalArgumentException("message is required");
        if (intent == null) throw new IllegalArgumentException("intent is required");
        if (decision == null) throw new IllegalArgumentException("decision is required");
        if (given == null) throw new IllegalArgumentException("given is required");
        if (conversation == null) throw new IllegalArgumentException("conversation is required");

        given = Map.copyOf(given);
    }
}
