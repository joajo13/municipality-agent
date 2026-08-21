package com.municipality.agent.policy;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides what the agent does about what the resident meant.
 *
 * <p>This is the deterministic half of the pipeline. A model works out the intent, and
 * from there on it is ordinary Java: the rules a municipality actually has are applied
 * the same way every time, and nothing a model returns can talk this into starting a
 * procedure it should not.
 *
 * <p>The rules are tried in order, and the order is the interesting part — see
 * {@link #decide}.
 */
public class Policy {

    /** Below this, half-understanding the request is worse than admitting it. */
    private static final double MINIMUM_CONFIDENCE = 0.5;

    /**
     * Works out the outcome for one message.
     *
     * <p>A handover is checked first, before confidence: somebody who asked for a person
     * gets one, and answering them with a menu instead would be the most annoying thing
     * this agent could do. After that, not being sure and not having understood both
     * lead to the menu. What is left is a request that was understood, and the only
     * question is whether everything it needs is already known.
     *
     * @param known what the resident has already provided, over this conversation
     */
    public Decision decide(Intent intent, Map<EntityType, String> known) {
        Domain domain = intent.domain();
        Action action = intent.action();

        if (action == Action.HANDOFF) return new Handoff(domain);
        if (intent.confidence() < MINIMUM_CONFIDENCE) return new FallbackMenu();
        if (domain == Domain.UNKNOWN) return new FallbackMenu();
        if (domain == Domain.SMALLTALK) return new Answer(domain);
        if (action == Action.INFORMATION) return new Answer(domain);

        Set<EntityType> missing = missingFor(domain, action, known);

        return missing.isEmpty() ? new StartFlow(domain, action, known) : new AskFor(domain, action, missing);
    }

    /** What this procedure needs, minus what the resident has already given. */
    private static Set<EntityType> missingFor(Domain domain, Action action, Map<EntityType, String> known) {
        var missing = new LinkedHashSet<>(domain.requires(action));
        missing.removeAll(known.keySet());

        return missing;
    }
}
