package com.municipality.agent.observability;

import java.time.Duration;

/**
 * One call to a language model: which one, what it was given, what it gave back, and how
 * long it took.
 *
 * <p>Tokens are counted rather than estimated. They come off the response the provider
 * sent, which is also what the invoice is built from — an estimate made here would
 * disagree with the bill, and the disagreement would be discovered a month later.
 *
 * @param model  what the provider says answered, which is not always what was asked for
 * @param took   wall time, including whatever the network added
 */
public record ModelCall(String model, long inputTokens, long outputTokens, Duration took) {

    public ModelCall {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("tokens must not be negative");
        if (took == null || took.isNegative()) throw new IllegalArgumentException("took must not be negative");
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
