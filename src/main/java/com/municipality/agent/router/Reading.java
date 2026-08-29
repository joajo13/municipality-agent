package com.municipality.agent.router;

/**
 * The shape the model is asked to answer in.
 *
 * <p>It is an {@link Intent} in every respect except that nothing about it has been
 * checked yet. Keeping the two apart is the point: this is what a model said, and an
 * {@code Intent} is what the rest of the agent is allowed to act on. Turning one into
 * the other is where the claim gets tested — a topic that does not exist, an action
 * left out, a confidence of 4.0, and the conversion throws rather than travelling on.
 *
 * <p>The field names are the JSON keys the model answers with, and the two enums become
 * the list of values it is allowed to choose from. Adding a domain therefore widens the
 * answer the model may give without a line of this file changing.
 */
record Reading(Domain domain, Action action, double confidence) {

    Intent asIntent() {
        return new Intent(domain, action, confidence);
    }
}
