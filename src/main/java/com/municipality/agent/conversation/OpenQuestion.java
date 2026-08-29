package com.municipality.agent.conversation;

import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;

import java.util.Set;

/**
 * Something the agent asked for and has not been given yet.
 *
 * <p>It carries the intent it was asked on behalf of, not just the list of what is
 * missing. That is what a one-word answer is measured against: "20123456" says nothing
 * about licences, and the only reason it can be read as a licence application is that
 * the question it answers was asked about one.
 *
 * @param missing what was asked for — never empty
 */
public record OpenQuestion(Intent intent, Set<EntityType> missing) {

    public OpenQuestion {
        if (intent == null) throw new IllegalArgumentException("intent is required");
        if (missing == null || missing.isEmpty()) throw new IllegalArgumentException("missing must not be empty");

        missing = Set.copyOf(missing);
    }
}
