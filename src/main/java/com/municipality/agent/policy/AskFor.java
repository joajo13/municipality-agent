package com.municipality.agent.policy;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;

import java.util.Set;

/**
 * The procedure is understood but something it needs is still missing. Ask for it before
 * going any further.
 *
 * @param missing at least one thing — never empty
 */
public record AskFor(Domain domain, Action action, Set<EntityType> missing) implements Decision {

    public AskFor {
        if (domain == null) throw new IllegalArgumentException("domain is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (missing == null || missing.isEmpty()) throw new IllegalArgumentException("missing must not be empty");

        missing = Set.copyOf(missing);
    }
}
