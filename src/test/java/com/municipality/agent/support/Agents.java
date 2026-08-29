package com.municipality.agent.support;

import com.municipality.agent.Agent;
import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.extraction.PatternEntityExtractor;
import com.municipality.agent.message.NoMediaDescriber;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.router.KeywordClassifier;

import java.time.Duration;

/**
 * The agent as it is actually assembled, for tests whose subject is not the assembling.
 *
 * <p>Real parts throughout. The only stand-ins are the ones that are the real thing when
 * nothing is configured — keywords instead of a model, a map instead of a database — so
 * nothing here reaches the network and nothing here needs a container.
 */
public final class Agents {

    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private Agents() {}

    /** The default assembly: keyword classification, conversations in memory. */
    public static Agent keyword() {
        return around(new KeywordClassifier(), new InMemoryConversations());
    }

    public static Agent around(Classifier classifier, Conversations conversations) {
        return new Agent(
                new Normalizer(new NoMediaDescriber()),
                new PatternEntityExtractor(),
                classifier,
                new Policy(),
                conversations,
                IDLE_TIMEOUT);
    }
}
