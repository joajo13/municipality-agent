package com.municipality.agent.router;

import com.municipality.agent.observability.ModelCall;
import org.jspecify.annotations.Nullable;

/**
 * What the classifier concluded, and what it took to conclude it.
 *
 * <p>The second half is here because somebody pays for it. A classifier that reaches a
 * model spends tokens and time on every message, and a number that only appears on next
 * month's invoice is a number nobody can act on. One that answers out of a word list
 * spends nothing, and says so by leaving the call empty.
 *
 * @param call what the model call cost, or {@code null} when nothing was called
 */
public record Classification(Intent intent, @Nullable ModelCall call) {

    public Classification {
        if (intent == null) throw new IllegalArgumentException("intent is required");
    }

    /** A conclusion nobody was billed for. */
    public static Classification free(Intent intent) {
        return new Classification(intent, null);
    }
}
