package com.municipality.agent;

import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.policy.Decision;
import com.municipality.agent.router.Intent;

/**
 * Everything one trip through the agent produced, not just the answer.
 *
 * <p>The decision alone would be enough to reply. The rest is here because a decision
 * that looks right for the wrong reason is indistinguishable from one that is right, and
 * the whole point of the console is to be able to tell them apart.
 *
 * @param message what was actually classified, which is not always what was typed
 */
public record Outcome(NormalizedMessage message, Intent intent, Decision decision) {

    public Outcome {
        if (message == null) throw new IllegalArgumentException("message is required");
        if (intent == null) throw new IllegalArgumentException("intent is required");
        if (decision == null) throw new IllegalArgumentException("decision is required");
    }
}
