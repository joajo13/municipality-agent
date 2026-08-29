package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.observability.ModelCall;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;

import java.time.Duration;

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
 * menu. An agent that cannot reach its model says it did not follow you; it does not fall
 * over, and it does not guess.
 *
 * <p>What every call cost comes back with the answer. The tokens are the provider's own
 * count off its own response, which is what the invoice is built from — an estimate made
 * here would disagree with the bill, and the disagreement would surface a month later.
 */
public class ModelClassifier implements Classifier {

    private static final Logger log = LoggerFactory.getLogger(ModelClassifier.class);

    /** What the provider is called when its response does not say. */
    private static final String UNNAMED_MODEL = "unknown";

    private static final BeanOutputConverter<Reading> READING = new BeanOutputConverter<>(Reading.class);

    /**
     * The instructions and the schema, together, and both in the system message.
     *
     * <p>Spring AI would happily staple the schema onto the end of the user message
     * instead. That message is a resident talking, and the less of this system's own
     * wording is mixed into it the less there is for a resident to answer back to.
     */
    private static final String SYSTEM = ClassificationPrompt.TEXT + "\n\n" + READING.getFormat();

    private final ChatClient chat;

    public ModelClassifier(ChatClient chat) {
        if (chat == null) throw new IllegalArgumentException("chat is required");

        this.chat = chat;
    }

    /**
     * Two steps, and they fail differently.
     *
     * <p>A call that never happened cost nothing, and there is no number to report. A
     * call that came back with nonsense cost exactly what a useful one would have: the
     * tokens were spent either way, and a ledger that counts only the answers it liked
     * understates the bill. So the cost is taken off the response before anybody tries to
     * make sense of it.
     *
     * <p>What the resident sees is the same in both cases. From where they are sitting a
     * timeout and a wrong answer are one event: the agent did not follow them.
     */
    @Override
    public Classification classify(NormalizedMessage message) {
        long startedAt = System.nanoTime();
        ChatResponse response;

        try {
            response = chat.prompt().system(SYSTEM).user(message.text()).call().chatResponse();
        } catch (RuntimeException neverArrived) {
            return unreadable(message, neverArrived.toString(), null);
        }

        ModelCall call = callOf(response, Duration.ofNanos(System.nanoTime() - startedAt));

        try {
            String answer = answerIn(response);

            if (answer == null) return unreadable(message, "the model answered with nothing", call);

            Reading reading = READING.convert(answer);

            if (reading == null) return unreadable(message, "the answer did not read as anything", call);

            return new Classification(reading.asIntent(), call);
        } catch (RuntimeException madeNoSense) {
            return unreadable(message, madeNoSense.toString(), call);
        }
    }

    /** The text of the answer, defended the whole way down: none of this response is ours. */
    private static @Nullable String answerIn(@Nullable ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) return null;

        String text = response.getResult().getOutput().getText();

        return text == null || text.isBlank() ? null : text;
    }

    /**
     * What the call cost, as the provider reported it.
     *
     * <p>Every field is defended, because none of it is ours. A response with no metadata,
     * no usage, or null token counts is a response from a provider having a bad day, and a
     * turn that answered a resident correctly must not then fail on the way to the ledger.
     */
    private static ModelCall callOf(@Nullable ChatResponse response, Duration took) {
        if (response == null || response.getMetadata() == null) return new ModelCall(UNNAMED_MODEL, 0, 0, took);

        var metadata = response.getMetadata();
        String model = metadata.getModel() == null || metadata.getModel().isBlank()
                ? UNNAMED_MODEL
                : metadata.getModel();

        Usage usage = metadata.getUsage();

        return new ModelCall(model, tokens(usage == null ? null : usage.getPromptTokens()),
                tokens(usage == null ? null : usage.getCompletionTokens()), took);
    }

    private static long tokens(@Nullable Integer counted) {
        return counted == null || counted < 0 ? 0L : counted;
    }

    /**
     * What a message nobody could read is worth. The trace id is logged because this is
     * the one thing that happens here and leaves no mark on the reply: the resident is
     * shown the same menu whether the model misunderstood them or was never reached.
     */
    private static Classification unreadable(NormalizedMessage message, String why, @Nullable ModelCall call) {
        log.warn("Could not classify message {}: {}", message.traceId(), why);

        return new Classification(new Intent(Domain.UNKNOWN, Action.INFORMATION, 0.0), call);
    }
}
