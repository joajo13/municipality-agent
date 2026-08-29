package com.municipality.agent.extraction;

import com.municipality.agent.router.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * Reads the data a procedure needs out of what a resident wrote.
 *
 * <p>Deliberately not the classifier's job. What somebody wants and what they have given
 * are different questions, they are wrong in different ways, and only one of them has a
 * right answer that can be checked: a dni either is eight digits or it is not.
 *
 * <p>The expected set is what makes a bare answer readable. "1234" on its own means
 * nothing; "1234" after the agent asked for a claim number is a claim number. Anything
 * the resident labelled — "mi dni es 20.123.456" — is read whether it was expected or
 * not, because they said what it was.
 */
public interface EntityExtractor {

    /**
     * @param expected what the agent is waiting for, if anything. An unlabelled value is
     *                 only ever read as the one thing that was asked for, so ambiguity
     *                 is resolved by the conversation rather than by guessing.
     * @return only what was found; never a key with no value behind it
     */
    Map<EntityType, String> extract(String text, Set<EntityType> expected);
}
