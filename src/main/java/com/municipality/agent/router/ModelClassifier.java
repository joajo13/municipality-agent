package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Works out what a resident wants by asking a language model, through Spring AI.
 *
 * <p>This is the one place in the agent where a model decides anything, and it decides
 * the smallest thing it can: a topic, an action and a number. What happens next is
 * {@code Policy}'s, and that is ordinary Java — so the worst a wrong answer here can do
 * is send somebody to the wrong procedure, never to one the rules do not allow.
 *
 * <p>The model is asked to answer in the shape of a {@link Reading}, so the topics and
 * actions it may choose from are the enums themselves rather than a list written out
 * somewhere and left to rot.
 *
 * <p>Nothing it returns is trusted. An answer that does not parse, a topic that does not
 * exist, a confidence of 4.0, a timeout, a rejected key — from here they are one and the
 * same thing, and the honest reading of all of them is that nothing was understood. That
 * is what {@link #unreadable} is: no confidence at all, which the policy turns into the
 * menu. An agent that cannot reach its model says it did not follow you; it does not
 * fall over, and it does not guess.
 */
public class ModelClassifier implements Classifier {

    private static final Logger log = LoggerFactory.getLogger(ModelClassifier.class);

    private final ChatClient chat;

    public ModelClassifier(ChatClient chat) {
        if (chat == null) throw new IllegalArgumentException("chat is required");

        this.chat = chat;
    }

    @Override
    public Intent classify(NormalizedMessage message) {
        try {
            Reading reading = chat.prompt()
                    .system(ClassificationPrompt.TEXT)
                    .user(message.text())
                    .call()
                    .entity(Reading.class);

            return reading == null ? unreadable(message, "the model answered with nothing") : reading.asIntent();
        } catch (RuntimeException failure) {
            // Deliberately everything: the call that never arrived and the answer that
            // made no sense are the same event as far as the resident is concerned.
            return unreadable(message, failure.toString());
        }
    }

    /**
     * What a message nobody could read is worth. The trace id is logged because this is
     * the one thing that happens here and leaves no mark on the reply: the resident is
     * shown the same menu whether the model misunderstood them or was never reached.
     */
    private static Intent unreadable(NormalizedMessage message, String why) {
        log.warn("Could not classify message {}: {}", message.traceId(), why);

        return new Intent(Domain.UNKNOWN, Action.INFORMATION, 0.0);
    }
}
