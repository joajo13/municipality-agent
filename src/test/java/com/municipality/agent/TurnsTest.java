package com.municipality.agent;

import com.municipality.agent.conversation.ConcurrentTurn;
import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.support.Agents;
import com.municipality.agent.support.Messages;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The counters and the span around a turn.
 *
 * <p>What is asserted here is mostly what is *not* recorded. Everything this class emits
 * ends up in a system with a longer memory and a wider audience than this one, and a
 * resident's phone number or a document number arriving there is not something a later
 * release can take back.
 */
class TurnsTest {

    private static final String RESIDENT = "+5493415551234";

    private final MeterRegistry meters = new SimpleMeterRegistry();
    private final Turns turns = Agents.watched(Agents.keyword(), meters);

    private Instant sentAt = Messages.FIRST_SENT_AT;

    private void say(String typed) {
        sentAt = sentAt.plus(Messages.BETWEEN_TURNS);
        turns.handle(Messages.from(RESIDENT, typed, sentAt));
    }

    private double counted(String metric, String... tags) {
        return meters.find(metric).tags(tags).counter().count();
    }

    @Test
    void everyTurnIsCountedByWhatItDecided() {
        say("quiero sacar la licencia");

        assertThat(counted("agent.turns", "domain", "LICENCIAS", "action", "START_PROCEDURE", "decision", "AskFor"))
                .isEqualTo(1);
    }

    @Test
    void turnsOfTheSameShapeCountTogether() {
        say("hola");
        say("gracias");

        assertThat(counted("agent.turns", "domain", "SMALLTALK", "action", "INFORMATION", "decision", "Answer"))
                .isEqualTo(2);
    }

    @Test
    void aTurnThatReachedNoModelCountsNoTokensAndNoMoney() {
        say("hola");

        assertThat(meters.find("agent.model.tokens").counter()).isNull();
        assertThat(meters.find("agent.model.cost").counter()).isNull();
        assertThat(meters.find("agent.model.calls").counter()).isNull();
    }

    @Test
    void whatAModelCostIsCounted() {
        var modelBacked = Agents.watched(
                Agents.around(Agents.spending(412, 18), new InMemoryConversations()), meters);

        modelBacked.handle(Messages.from(RESIDENT, "quiero sacar la licencia", sentAt));

        assertThat(counted("agent.model.tokens", "model", Agents.PRICED_MODEL, "direction", "input")).isEqualTo(412);
        assertThat(counted("agent.model.tokens", "model", Agents.PRICED_MODEL, "direction", "output")).isEqualTo(18);
        assertThat(counted("agent.model.calls", "model", Agents.PRICED_MODEL, "priced", "true")).isEqualTo(1);
        assertThat(counted("agent.model.cost", "model", Agents.PRICED_MODEL, "currency", "USD"))
                .isEqualTo(0.000502, org.assertj.core.data.Offset.offset(0.0000001));
    }

    @Test
    void aModelNobodyPricedIsCountedAsSuchRatherThanHidden() {
        var unpriced = Agents.watched(
                Agents.around(Agents.spendingAs("a-model-nobody-priced", 100, 10), new InMemoryConversations()),
                meters);

        unpriced.handle(Messages.from(RESIDENT, "hola", sentAt));

        assertThat(counted("agent.model.calls", "model", "a-model-nobody-priced", "priced", "false")).isEqualTo(1);
    }

    @Test
    void theResidentIsNotLeftBehindInTheLoggingContext() {
        // MDC is thread-local and threads are reused. Anything left in there is attached
        // to whatever that thread handles next, which will be a different resident.
        say("hola");

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("resident")).isNull();
    }

    @Test
    void aTurnThatFailsCleansUpAfterItself() {
        Agent failing = Agents.around(new com.municipality.agent.router.KeywordClassifier(), refusesToWrite());
        Turns watched = Agents.watched(failing, meters);

        assertThatThrownBy(() -> watched.handle(Messages.from(RESIDENT, "hola", sentAt)))
                .isInstanceOf(ConcurrentTurn.class);

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("resident")).isNull();
    }

    private static Conversations refusesToWrite() {
        return new Conversations() {
            @Override
            public Optional<Conversation> of(String userId) {
                return Optional.empty();
            }

            @Override
            public Conversation save(Conversation conversation) {
                throw new ConcurrentTurn(conversation.userId());
            }
        };
    }
}
