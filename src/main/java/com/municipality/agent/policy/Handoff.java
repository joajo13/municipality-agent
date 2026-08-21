package com.municipality.agent.policy;

import com.municipality.agent.router.Domain;

/**
 * Stop answering and put a person on.
 *
 * <p>Carries the topic because whoever picks the conversation up needs to know what it
 * was about before reading a word of it. The topic may well be {@link Domain#UNKNOWN}:
 * asking for a human is a complete request on its own.
 *
 * <p>Today the only way to get here is by asking. Later there will be others — the same
 * question misunderstood three times over, an angry resident, a procedure the agent
 * cannot finish — and when a second one exists, this will carry the reason too.
 */
public record Handoff(Domain domain) implements Decision {

    public Handoff {
        if (domain == null) throw new IllegalArgumentException("domain is required");
    }
}
