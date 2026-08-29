package com.municipality.agent.api;

import com.municipality.agent.Outcome;
import com.municipality.agent.console.DecisionRenderer;
import com.municipality.agent.observability.ModelCall;
import com.municipality.agent.router.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What the agent did about a message, as it goes back over the wire.
 *
 * <p>What a resident reads is one field of this. The rest is for whoever is integrating:
 * what was understood, what is still being waited on, and what the turn cost. A channel
 * that only wants to forward the reply can ignore all of it.
 *
 * <p>Nothing here is a value the resident gave. {@code known} and {@code awaiting} are
 * lists of names — the caller already has the document number they sent, and this
 * response ends up in their logs too.
 */
public record TurnResponse(
        String messageId,
        String reply,
        String decision,
        IntentView intent,
        ConversationView conversation,
        @Nullable UsageView usage) {

    public record IntentView(String domain, String action, double confidence) {}

    /**
     * @param turn      which turn of this conversation was just handled
     * @param known     the names of what the resident has given, never the values
     * @param awaiting  the names of what the agent is waiting to be told
     */
    public record ConversationView(int turn, List<String> known, List<String> awaiting) {}

    /**
     * @param cost as a string, because a JSON number would round it and this is money
     */
    public record UsageView(
            String model,
            long inputTokens,
            long outputTokens,
            String cost,
            String currency,
            long tookMillis) {}

    public static TurnResponse of(Outcome outcome, DecisionRenderer renderer) {
        var intent = outcome.intent();
        var trace = outcome.trace();

        return new TurnResponse(
                trace.traceId(),
                renderer.reply(outcome.decision()),
                renderer.summary(outcome.decision()),
                new IntentView(intent.domain().name(), intent.action().name(), intent.confidence()),
                new ConversationView(
                        outcome.conversation().turns(),
                        names(outcome.conversation().known().keySet()),
                        names(outcome.conversation().expecting())),
                usageOf(outcome));
    }

    private static @Nullable UsageView usageOf(Outcome outcome) {
        ModelCall call = outcome.trace().call();

        if (call == null) return null;

        return new UsageView(
                call.model(),
                call.inputTokens(),
                call.outputTokens(),
                outcome.trace().cost().amount().toPlainString(),
                outcome.trace().cost().currency(),
                outcome.trace().took().toMillis());
    }

    /** Sorted, so that two identical turns produce two identical responses. */
    private static List<String> names(java.util.Collection<EntityType> entities) {
        SortedSet<String> sorted = new TreeSet<>();
        entities.forEach(entity -> sorted.add(entity.name()));

        return List.copyOf(sorted);
    }
}
