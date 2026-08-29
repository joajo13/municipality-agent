package com.municipality.agent;

import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.OpenQuestion;
import com.municipality.agent.extraction.EntityExtractor;
import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.policy.Answer;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.policy.Decision;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.policy.Handoff;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.policy.StartFlow;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * One turn of a conversation, from what arrived to what the agent will do about it.
 *
 * <p>The order is the design. What the resident gave is read before what they meant is
 * decided, because a message may be nothing but an answer to the last question; and the
 * policy is asked last, with everything known by then, because it is the only part whose
 * answer is allowed to be acted on.
 *
 * <p>Nothing about a console appears here, and nothing about WhatsApp does either. This
 * is what the agent does with a message; where it came from, who reads the answer, and
 * where the conversation is kept are all somebody else's problem.
 *
 * <p>It holds no state of its own. Everything it remembers goes through
 * {@link Conversations}, so a second instance of the service answers the same message
 * the same way, and a turn that races another one loses cleanly rather than overwriting
 * it.
 */
public class Agent {

    private final Normalizer normalizer;
    private final EntityExtractor extractor;
    private final Classifier classifier;
    private final Policy policy;
    private final Conversations conversations;
    private final Duration idleTimeout;

    public Agent(
            Normalizer normalizer,
            EntityExtractor extractor,
            Classifier classifier,
            Policy policy,
            Conversations conversations,
            Duration idleTimeout) {

        if (normalizer == null) throw new IllegalArgumentException("normalizer is required");
        if (extractor == null) throw new IllegalArgumentException("extractor is required");
        if (classifier == null) throw new IllegalArgumentException("classifier is required");
        if (policy == null) throw new IllegalArgumentException("policy is required");
        if (conversations == null) throw new IllegalArgumentException("conversations is required");
        if (idleTimeout == null || idleTimeout.isNegative()) throw new IllegalArgumentException("idleTimeout");

        this.normalizer = normalizer;
        this.extractor = extractor;
        this.classifier = classifier;
        this.policy = policy;
        this.conversations = conversations;
        this.idleTimeout = idleTimeout;
    }

    public Outcome handle(IncomingMessage message) {
        NormalizedMessage normalized = normalizer.normalize(message);
        Conversation opening = remembered(message);

        Map<EntityType, String> given = extractor.extract(normalized.text(), opening.expecting());
        Conversation told = opening.learned(given);

        Intent intent = told.read(classifier.classify(normalized));
        Decision decision = policy.decide(intent, told.known());

        Conversation closing = conversations.save(told.after(waitingFor(decision, intent, told), message.timestamp()));

        return new Outcome(normalized, intent, decision, given, closing);
    }

    /** What is remembered about this resident, unless it is old enough to be somebody else's day. */
    private Conversation remembered(IncomingMessage message) {
        Instant now = message.timestamp();

        return conversations.of(message.userId())
                .map(conversation -> conversation.isOpenAt(now, idleTimeout) ? conversation : conversation.forgotten(now))
                .orElseGet(() -> Conversation.startedBy(message.userId(), now));
    }

    /**
     * What the agent will be waiting for when the next message arrives.
     *
     * <p>Asking sets the question. Starting a procedure and handing over both settle it —
     * there is nothing left to wait for, or a person is waiting instead. Answering and
     * the menu leave it exactly where it was: "gracias" in the middle of a procedure does
     * not abandon the procedure, and neither does a sentence the agent could not follow.
     *
     * <p>No {@code default} branch: a sixth decision stops the build here until somebody
     * says what it does to the thread of the conversation.
     */
    private static @Nullable OpenQuestion waitingFor(Decision decision, Intent intent, Conversation conversation) {
        return switch (decision) {
            case AskFor askFor -> new OpenQuestion(intent, askFor.missing());
            case StartFlow ignored -> null;
            case Handoff ignored -> null;
            case Answer ignored -> conversation.asked();
            case FallbackMenu() -> conversation.asked();
        };
    }
}
