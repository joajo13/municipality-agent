package com.municipality.agent.policy;

/**
 * It did not work out what the resident wanted, or it is not sure enough to act. Say so
 * and show what there is on offer.
 *
 * <p>Carries nothing: there is no topic to carry, which is the whole reason this is the
 * outcome. Note that this is not the same as {@link Handoff} — the menu means "I did not
 * follow you", a handover means "I followed you, and a person takes it from here".
 */
public record FallbackMenu() implements Decision {}
