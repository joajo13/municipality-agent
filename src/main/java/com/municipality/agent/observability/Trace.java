package com.municipality.agent.observability;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * What one turn took to answer, and what it cost.
 *
 * <p>It travels with the answer rather than being logged and forgotten, because the two
 * questions people ask about an agent like this — why did it say that, and what is it
 * costing us — are asked about the same turn and are worth being able to line up.
 *
 * @param traceId the id the message arrived with, which is what ties this to every log
 *                line the turn produced
 * @param took    the whole turn: reading the message, the model, the rules, the write
 * @param call    what was asked of a model, or {@code null} when nothing was
 */
public record Trace(String traceId, Duration took, @Nullable ModelCall call, Cost cost) {

    public Trace {
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId must not be blank");
        if (took == null || took.isNegative()) throw new IllegalArgumentException("took must not be negative");
        if (cost == null) throw new IllegalArgumentException("cost is required");
    }

    /** Whether a model was involved at all. A turn answered from a word list is free. */
    public boolean reachedAModel() {
        return call != null;
    }
}
