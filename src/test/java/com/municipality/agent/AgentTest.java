package com.municipality.agent;

import com.municipality.agent.conversation.ConcurrentTurn;
import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.message.Image;
import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.policy.Answer;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.policy.Handoff;
import com.municipality.agent.policy.StartFlow;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.support.Agents;
import com.municipality.agent.support.Messages;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One turn, and then the next one: what arrived becomes text, what the resident handed
 * over is read, the text becomes an intent, and the intent becomes a decision — with
 * everything the conversation already knew standing behind it.
 *
 * <p>Real collaborators throughout, no stand-ins beyond the ones that are the real thing
 * when nothing is configured. Nothing here reaches the network, so there is nothing to
 * fake.
 */
class AgentTest {

    private static final String RESIDENT = "user-1";

    private final Conversations conversations = new InMemoryConversations();
    private final Agent agent = Agents.around(new com.municipality.agent.router.KeywordClassifier(), conversations);

    private Instant sentAt = Messages.FIRST_SENT_AT;

    /** Types one line, a minute after the last one. */
    private Outcome handle(String typed) {
        sentAt = sentAt.plus(Messages.BETWEEN_TURNS);

        return agent.handle(Messages.from(RESIDENT, typed, sentAt));
    }

    // --- one turn at a time --------------------------------------------------

    @Test
    void reportingSomethingBrokenStartsAComplaint() {
        var outcome = handle("se rompio una luminaria en Sarmiento 450");

        assertThat(outcome.intent().domain()).isEqualTo(Domain.RECLAMOS);
        assertThat(outcome.intent().action()).isEqualTo(Action.START_PROCEDURE);
        assertThat(outcome.decision()).isInstanceOf(StartFlow.class);
    }

    @Test
    void askingAfterAComplaintAsksForItsNumber() {
        var outcome = handle("quiero consultar el estado de mi reclamo");

        assertThat(outcome.decision()).isInstanceOfSatisfying(AskFor.class,
                askFor -> assertThat(askFor.missing()).containsExactly(CLAIM_NUMBER));
    }

    @Test
    void somethingItCannotPlaceGetsTheMenu() {
        assertThat(handle("esta lloviendo muchisimo").decision()).isInstanceOf(FallbackMenu.class);
    }

    @Test
    void askingForAPersonHandsOver() {
        assertThat(handle("quiero hablar con una persona").decision()).isInstanceOf(Handoff.class);
    }

    @Test
    void theOutcomeKeepsTheTextThatWasClassified() {
        // The console shows this, and it is not always what the resident typed: media is
        // announced, whitespace goes, long messages are cut.
        var outcome = handle("   QUIERO LA LICENCIA   ");

        assertThat(outcome.message().text()).isEqualTo("QUIERO LA LICENCIA");
    }

    @Test
    void aPhotoNobodyHasLookedAtDoesNotRouteAnywhereByAccident() {
        // Once it reaches the classifier a placeholder is a word like any other. The day
        // "imagen" or "documento" becomes a keyword, sending a photo would quietly route
        // somebody into a procedure they never asked for.
        var photo = new Image("https://cdn.example/img/1.jpg", null);

        var outcome = agent.handle(Messages.of(RESIDENT, sentAt, photo));

        assertThat(outcome.message().text()).isEqualTo("[imagen]");
        assertThat(outcome.intent().domain()).isEqualTo(Domain.UNKNOWN);
    }

    @Test
    void theTraceIdSurvivesTheWholePipeline() {
        // One conversation has to be followable across logs from end to end.
        IncomingMessage incoming = Messages.from(RESIDENT, "hola", sentAt);

        assertThat(agent.handle(incoming).message().traceId()).isEqualTo(incoming.traceId());
    }

    // --- one turn after another ----------------------------------------------

    @Test
    void aBareNumberAnswersTheQuestionThatWasAsked() {
        handle("quiero consultar el estado de mi reclamo");

        var outcome = handle("4471");

        assertThat(outcome.given()).containsEntry(CLAIM_NUMBER, "4471");
        assertThat(outcome.intent().domain()).isEqualTo(Domain.RECLAMOS);
        assertThat(outcome.intent().action()).isEqualTo(Action.CHECK_STATUS);
        assertThat(outcome.decision()).isInstanceOf(StartFlow.class);
    }

    @Test
    void whatTheProcedureNeedsTravelsWithIt() {
        handle("quiero consultar el estado de mi reclamo");

        var outcome = handle("4471");

        assertThat(outcome.decision()).isInstanceOfSatisfying(StartFlow.class,
                flow -> assertThat(flow.entities()).containsEntry(CLAIM_NUMBER, "4471"));
    }

    @Test
    void nothingIsAskedForTwice() {
        handle("quiero sacar la licencia");
        handle("mi dni es 20.123.456");

        var outcome = handle("quiero un turno en el hospital");

        assertThat(outcome.decision()).isInstanceOf(StartFlow.class);
        assertThat(outcome.conversation().known()).containsEntry(DNI, "20123456");
    }

    @Test
    void changingTheSubjectIsAllowedMidQuestion() {
        handle("quiero consultar el estado de mi reclamo");

        var outcome = handle("mejor quiero sacar la licencia de conducir");

        assertThat(outcome.intent().domain()).isEqualTo(Domain.LICENCIAS);
    }

    @Test
    void askingForAPersonIsNeverReadAsAnAnswer() {
        handle("quiero consultar el estado de mi reclamo");

        assertThat(handle("quiero hablar con una persona").decision()).isInstanceOf(Handoff.class);
    }

    @Test
    void aGreetingDoesNotAbandonTheProcedure() {
        handle("quiero consultar el estado de mi reclamo");

        assertThat(handle("gracias!").decision()).isInstanceOf(Answer.class);

        var outcome = handle("4471");

        assertThat(outcome.decision()).isInstanceOf(StartFlow.class);
    }

    @Test
    void aMessageNobodyFollowedDoesNotAbandonTheProcedureEither() {
        handle("quiero consultar el estado de mi reclamo");
        handle("esta lloviendo muchisimo");

        assertThat(handle("4471").decision()).isInstanceOf(StartFlow.class);
    }

    @Test
    void aStreetNumberIsNotAnAnswerToAnythingNobodyAsked() {
        var outcome = handle("se rompio una luminaria en Sarmiento 450");

        assertThat(outcome.given()).isEmpty();
    }

    @Test
    void comingBackTheNextDayStartsAgain() {
        handle("quiero consultar el estado de mi reclamo");

        sentAt = sentAt.plus(Duration.ofHours(8));

        var outcome = agent.handle(Messages.from(RESIDENT, "4471", sentAt));

        // Yesterday's question is not still standing, so a number on its own means
        // nothing again -- and nothing is filed under it.
        assertThat(outcome.decision()).isInstanceOf(FallbackMenu.class);
        assertThat(outcome.conversation().known()).isEmpty();
        assertThat(outcome.given()).isEmpty();
    }

    @Test
    void turnsAreStillCountedAcrossAConversationThatStartedAgain() {
        // The memory is gone; the count is not. It is what says which write comes next,
        // and a resident coming back does not undo the turns already handled.
        handle("hola");

        sentAt = sentAt.plus(Duration.ofHours(8));

        assertThat(agent.handle(Messages.from(RESIDENT, "hola", sentAt)).conversation().turns()).isEqualTo(2);
    }

    @Test
    void twoResidentsDoNotShareAnything() {
        handle("quiero sacar la licencia");
        handle("mi dni es 20.123.456");

        var other = agent.handle(Messages.from("user-2", "quiero sacar la licencia", sentAt));

        assertThat(other.conversation().known()).isEmpty();
        assertThat(other.decision()).isInstanceOfSatisfying(AskFor.class,
                askFor -> assertThat(askFor.missing()).containsExactly(DNI));
    }

    @Test
    void everyTurnIsCounted() {
        handle("hola");
        handle("quiero sacar la licencia");

        assertThat(handle("gracias").conversation().turns()).isEqualTo(3);
    }

    @Test
    void anAgentIsNotAssembledWithAPieceMissing() {
        // Every one of these is a null pointer somewhere in the middle of answering a
        // resident. Refusing to be built is the cheapest place to find out.
        var normalizer = new com.municipality.agent.message.Normalizer(
                new com.municipality.agent.message.NoMediaDescriber());
        var extractor = new com.municipality.agent.extraction.PatternEntityExtractor();
        var classifier = new com.municipality.agent.router.KeywordClassifier();
        var policy = new com.municipality.agent.policy.Policy();
        var costs = new com.municipality.agent.observability.Costs(Agents.PRICES);
        var idle = Agents.IDLE_TIMEOUT;

        assertThatThrownBy(() -> new Agent(null, extractor, classifier, policy, conversations, costs, idle))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, null, classifier, policy, conversations, costs, idle))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, extractor, null, policy, conversations, costs, idle))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, extractor, classifier, null, conversations, costs, idle))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, extractor, classifier, policy, null, costs, idle))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, extractor, classifier, policy, conversations, null, idle))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, extractor, classifier, policy, conversations, costs, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Agent(normalizer, extractor, classifier, policy, conversations, costs,
                Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTurnThatLosesARaceIsNotSilentlyDropped() {
        // Two messages from the same resident, handled at once by two instances. The one
        // that gets there second has to be told, or an answer is written over.
        var racing = new Conversations() {
            @Override
            public java.util.Optional<Conversation> of(String userId) {
                return conversations.of(userId);
            }

            @Override
            public Conversation save(Conversation conversation) {
                conversations.save(conversation);
                return conversations.save(conversation);
            }
        };

        var racingAgent = Agents.around(new com.municipality.agent.router.KeywordClassifier(), racing);

        assertThatThrownBy(() -> racingAgent.handle(Messages.from(RESIDENT, "hola", sentAt)))
                .isInstanceOf(ConcurrentTurn.class);
    }
}
