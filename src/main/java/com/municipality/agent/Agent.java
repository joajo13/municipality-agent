package com.municipality.agent;

import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.policy.Decision;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.router.Intent;

import java.util.Map;

/**
 * The three stages, joined up: what arrived becomes text, the text becomes an intent, and
 * the intent becomes a decision.
 *
 * <p>Nothing about a console appears here, and nothing about WhatsApp will either. This
 * is what the agent does with a message; where the message came from and who reads the
 * answer are somebody else's problem.
 */
public class Agent {

    private final Normalizer normalizer;
    private final Classifier classifier;
    private final Policy policy;

    public Agent(Normalizer normalizer, Classifier classifier, Policy policy) {
        if (normalizer == null) throw new IllegalArgumentException("normalizer is required");
        if (classifier == null) throw new IllegalArgumentException("classifier is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");

        this.normalizer = normalizer;
        this.classifier = classifier;
        this.policy = policy;
    }

    public Outcome handle(IncomingMessage message) {
        NormalizedMessage normalized = normalizer.normalize(message);
        Intent intent = classifier.classify(normalized);

        // Nothing is ever known yet: there is no extractor to read a dni out of a line,
        // and no session to remember one from the turn before. Until both exist, every
        // procedure that needs something will ask for it again each time.
        Decision decision = policy.decide(intent, Map.of());

        return new Outcome(normalized, intent, decision);
    }
}
