package com.municipality.agent.router;

import java.util.Set;

import static com.municipality.agent.router.Action.CHECK_STATUS;
import static com.municipality.agent.router.Action.HANDOFF;
import static com.municipality.agent.router.Action.INFORMATION;
import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;

/**
 * What a resident is talking about.
 *
 * <p>Declaration order is part of the behaviour: {@link KeywordClassifier} walks these
 * in order and keeps the first that matches, so the real topics come before
 * {@link #SMALLTALK}. Otherwise the "hola" that opens almost every message would win
 * over the request that follows it.
 */
public enum Domain {

    SALUD,
    LICENCIAS,
    RECLAMOS,

    /** Greetings, thanks, goodbyes. Nothing to do but answer. */
    SMALLTALK,

    /** Nothing here was recognised. Not a claim that it is none of our business. */
    UNKNOWN;

    /**
     * What the resident has to provide before this can be acted on.
     *
     * <p>The domain alone does not answer this. Checking a complaint needs its number;
     * filing one cannot, because the number is what filing it produces. And no question
     * or handover ever needs identity: opening hours are public, and whatever is missing
     * becomes the human's problem the moment one takes over.
     */
    public Set<EntityType> requires(Action action) {
        if (action == INFORMATION || action == HANDOFF) return Set.of();

        return switch (this) {
            case SALUD, LICENCIAS -> Set.of(DNI);
            case RECLAMOS -> action == CHECK_STATUS ? Set.of(CLAIM_NUMBER) : Set.of();
            case SMALLTALK, UNKNOWN -> Set.of();
        };
    }
}
