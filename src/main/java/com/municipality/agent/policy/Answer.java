package com.municipality.agent.policy;

import com.municipality.agent.router.Domain;

/**
 * Nothing to start and nothing to ask for: the resident asked something, or said hello.
 * Reply.
 *
 * <p>Carries the topic rather than the words. Finding the words is a separate job — the
 * municipality's own documentation for a question, something friendly for a greeting —
 * and it is the one place in this pipeline where a model does the heavy lifting.
 */
public record Answer(Domain domain) implements Decision {

    public Answer {
        if (domain == null) throw new IllegalArgumentException("domain is required");
    }
}
