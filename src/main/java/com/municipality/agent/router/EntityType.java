package com.municipality.agent.router;

/**
 * A piece of data the municipality needs before it can act on a request.
 *
 * <p>Only the two that some domain actually asks for. Others — CUIL, a number plate —
 * arrive with the domain that needs them, not before.
 */
public enum EntityType {

    /** National identity number. Identifies the resident. */
    DNI,

    /** The number a complaint got when it was filed. Identifies the complaint. */
    CLAIM_NUMBER
}
