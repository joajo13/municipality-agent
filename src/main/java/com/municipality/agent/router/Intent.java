package com.municipality.agent.router;

/**
 * What the classifier made of a message: the topic, what to do about it, and how sure
 * it is.
 *
 * <p>The confidence is guarded because of who fills it next. A language model is asked
 * for a number and answers with whatever it answers; without this, a 4.0 would travel
 * silently into whichever threshold decides between acting and asking again.
 *
 * @param confidence between 0.0 and 1.0 inclusive
 */
public record Intent(Domain domain, Action action, double confidence) {

    public Intent {
        if (domain == null) throw new IllegalArgumentException("domain is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be in 0..1");
    }
}
