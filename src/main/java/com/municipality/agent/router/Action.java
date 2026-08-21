package com.municipality.agent.router;

/**
 * What the resident wants done about the topic they raised.
 *
 * <p>This is separate from {@link Domain} on purpose. Handing over to a person is an
 * action rather than a domain: someone can need a human about a complaint, a licence or
 * anything else, and modelling it as a topic would throw away the one thing the person
 * taking over needs to know.
 */
public enum Action {

    /** Begin something that did not exist before: book, file, apply. */
    START_PROCEDURE,

    /** Ask how something already under way is going. */
    CHECK_STATUS,

    /** Ask a question. Nothing gets created or changed. */
    INFORMATION,

    /** Stop answering and put a person on. */
    HANDOFF
}
