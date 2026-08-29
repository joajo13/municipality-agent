package com.municipality.agent.support;

import com.municipality.agent.Agent;
import com.municipality.agent.observability.Pseudonyms;
import com.municipality.agent.Turns;
import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.extraction.PatternEntityExtractor;
import com.municipality.agent.message.NoMediaDescriber;
import com.municipality.agent.observability.Costs;
import com.municipality.agent.observability.PricingProperties;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.observability.ModelCall;
import com.municipality.agent.router.Classification;
import com.municipality.agent.router.KeywordClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * The agent as it is actually assembled, for tests whose subject is not the assembling.
 *
 * <p>Real parts throughout. The only stand-ins are the ones that are the real thing when
 * nothing is configured — keywords instead of a model, a map instead of a database — so
 * nothing here reaches the network and nothing here needs a container.
 */
public final class Agents {

    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /** A model that costs a round number, so that a cost in a test is readable at a glance. */
    public static final String PRICED_MODEL = "test-model";

    public static final PricingProperties PRICES = new PricingProperties("USD",
            Map.of(PRICED_MODEL, new PricingProperties.Price(new BigDecimal("1.00"), new BigDecimal("5.00"))));

    private Agents() {}

    /** The default assembly: keyword classification, conversations in memory. */
    public static Agent keyword() {
        return around(new KeywordClassifier(), new InMemoryConversations());
    }

    /** The same agent with the counters around it, which is what every caller actually holds. */
    public static Turns watched(Agent agent) {
        return watched(agent, new SimpleMeterRegistry());
    }

    public static Turns watched(Agent agent, MeterRegistry meters) {
        return new Turns(agent, new Costs(PRICES), new Pseudonyms("test-secret"), ObservationRegistry.NOOP, meters);
    }

    /** A classifier that answers like the word list but bills like a model. */
    public static Classifier spending(long inputTokens, long outputTokens) {
        return spendingAs(PRICED_MODEL, inputTokens, outputTokens);
    }

    public static Classifier spendingAs(String model, long inputTokens, long outputTokens) {
        var keywords = new KeywordClassifier();

        return message -> new Classification(
                keywords.classify(message).intent(),
                new ModelCall(model, inputTokens, outputTokens, Duration.ofMillis(300)));
    }

    public static Agent around(Classifier classifier, Conversations conversations) {
        return new Agent(
                new Normalizer(new NoMediaDescriber()),
                new PatternEntityExtractor(),
                classifier,
                new Policy(),
                conversations,
                new Costs(PRICES),
                IDLE_TIMEOUT);
    }
}
