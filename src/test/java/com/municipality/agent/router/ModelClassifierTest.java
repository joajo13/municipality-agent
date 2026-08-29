package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classifier that asks a model, with a model that never leaves the room.
 *
 * <p>What is faked is the model itself, not Spring AI: the answer below travels through
 * the real {@link ChatClient} and the real structured-output conversion, which is where
 * an answer would actually come apart.
 *
 * <p>Most of these are about what happens when the answer is wrong, because that is the
 * half a real model will exercise. It can time out, it can be handed a rejected key, and
 * it can cheerfully invent a topic that does not exist — and none of those may reach a
 * resident as anything but "I did not follow you".
 */
class ModelClassifierTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-22T10:00:00Z");

    /** A model that always answers the same thing, and remembers what it was asked. */
    private static final class CannedModel implements ChatModel {

        private final String answer;
        private Prompt asked = new Prompt(List.of());

        private CannedModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.asked = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }

        /** Everything the model was told, system prompt and message alike. */
        private String everythingItWasTold() {
            return asked.getInstructions().stream().map(Message::getText).collect(Collectors.joining("\n"));
        }
    }

    /** A model that cannot be reached at all. */
    private static final class UnreachableModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new IllegalStateException("connection refused");
        }
    }

    private static Intent classifiedBy(ChatModel model) {
        var classifier = new ModelClassifier(ChatClient.create(model));

        return classifier.classify(new NormalizedMessage("trace-1", "user-1", SENT_AT, "quiero sacar la licencia"));
    }

    /** What the classifier makes of {@code answer} coming back from the model. */
    private static Intent given(String answer) {
        return classifiedBy(new CannedModel(answer));
    }

    // --- reading an answer ---------------------------------------------------

    @Test
    void takesTheTopicTheModelAnsweredWith() {
        var intent = given("""
                {"domain": "LICENCIAS", "action": "START_PROCEDURE", "confidence": 0.9}
                """);

        assertThat(intent.domain()).isEqualTo(Domain.LICENCIAS);
        assertThat(intent.action()).isEqualTo(Action.START_PROCEDURE);
        assertThat(intent.confidence()).isEqualTo(0.9);
    }

    @Test
    void anAnswerWrappedInMarkdownIsStillAnAnswer() {
        // Models fence their JSON whether or not anybody asked them to.
        var intent = given("""
                ```json
                {"domain": "SALUD", "action": "INFORMATION", "confidence": 0.8}
                ```
                """);

        assertThat(intent.domain()).isEqualTo(Domain.SALUD);
    }

    // --- what it asks --------------------------------------------------------

    @Test
    void showsTheModelTheMessage() {
        var model = new CannedModel("""
                {"domain": "LICENCIAS", "action": "START_PROCEDURE", "confidence": 0.9}
                """);

        classifiedBy(model);

        assertThat(model.everythingItWasTold()).contains("quiero sacar la licencia");
    }

    @Test
    void tellsTheModelAboutEveryTopicAndEveryActionThereIs() {
        // The prompt is built from the enums for exactly this reason: a topic nobody
        // mentioned is a topic the model will never answer with.
        var model = new CannedModel("""
                {"domain": "LICENCIAS", "action": "START_PROCEDURE", "confidence": 0.9}
                """);

        classifiedBy(model);

        var told = model.everythingItWasTold();

        for (Domain domain : Domain.values()) assertThat(told).contains(domain.name());
        for (Action action : Action.values()) assertThat(told).contains(action.name());
    }

    // --- when the answer is not one ------------------------------------------

    @Test
    void anAnswerThatIsNotJsonIsNoAnswerAtAll() {
        assertThat(given("Creo que quiere sacar la licencia.").confidence()).isEqualTo(0.0);
    }

    @Test
    void aTopicItInventedIsNoAnswerAtAll() {
        // There is no MASCOTAS. Half-reading this into the nearest real topic is how a
        // resident ends up in a procedure nobody asked for.
        var intent = given("""
                {"domain": "MASCOTAS", "action": "START_PROCEDURE", "confidence": 1.0}
                """);

        assertThat(intent.domain()).isEqualTo(Domain.UNKNOWN);
        assertThat(intent.confidence()).isEqualTo(0.0);
    }

    @Test
    void aConfidenceThatIsNotAConfidenceIsNoAnswerAtAll() {
        // A model asked for a number between 0 and 1 answers with whatever it answers.
        // Left alone, a 4.0 sails through every threshold there is.
        assertThat(given("""
                {"domain": "LICENCIAS", "action": "START_PROCEDURE", "confidence": 4.0}
                """).confidence()).isEqualTo(0.0);
    }

    @Test
    void anAnswerMissingTheActionIsNoAnswerAtAll() {
        var intent = given("""
                {"domain": "LICENCIAS", "confidence": 0.9}
                """);

        assertThat(intent.domain()).isEqualTo(Domain.UNKNOWN);
        assertThat(intent.confidence()).isEqualTo(0.0);
    }

    // --- when there is no answer ---------------------------------------------

    @Test
    void aModelItCannotReachIsNotAnError() {
        // The network is down and somebody is still waiting to be answered. They get
        // the menu, which is what the agent says when it did not follow them.
        var intent = classifiedBy(new UnreachableModel());

        assertThat(intent.domain()).isEqualTo(Domain.UNKNOWN);
        assertThat(intent.action()).isEqualTo(Action.INFORMATION);
        assertThat(intent.confidence()).isEqualTo(0.0);
    }

    @Test
    void notUnderstandingIsNeverMistakenForAHandoff() {
        // Nothing that goes wrong here may put a person on: a handover is something a
        // resident asks for, not what an agent does when its model stops answering.
        assertThat(classifiedBy(new UnreachableModel()).action()).isNotEqualTo(Action.HANDOFF);
    }
}
