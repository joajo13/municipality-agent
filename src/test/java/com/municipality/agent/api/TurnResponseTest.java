package com.municipality.agent.api;

import com.municipality.agent.Outcome;
import com.municipality.agent.console.DecisionRenderer;
import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.OpenQuestion;
import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.observability.Cost;
import com.municipality.agent.observability.ModelCall;
import com.municipality.agent.observability.Trace;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.Intent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An outcome on its way back over the wire.
 *
 * <p>The assertions that matter are about what is missing from it. The names of what is
 * known, never the values; a cost as a string, because money in a JSON number is money
 * that has been rounded by somebody's parser.
 */
class TurnResponseTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    private static final Intent CHECKING = new Intent(Domain.RECLAMOS, Action.CHECK_STATUS, 0.85);

    private final DecisionRenderer renderer = new DecisionRenderer();

    private static Outcome outcomeWith(Trace trace) {
        var conversation = Conversation.startedBy("user-1", NOON)
                .learned(Map.of(DNI, "20123456"))
                .after(new OpenQuestion(CHECKING, Set.of(CLAIM_NUMBER)), NOON);

        return new Outcome(
                new NormalizedMessage("trace-1", "user-1", NOON, "el estado de mi reclamo"),
                CHECKING,
                new AskFor(Domain.RECLAMOS, Action.CHECK_STATUS, Set.of(CLAIM_NUMBER)),
                Map.of(DNI, "20123456"),
                conversation,
                trace);
    }

    private static Trace free() {
        return new Trace("trace-1", Duration.ofMillis(4), null, Cost.nothing("USD"));
    }

    private static Trace paidFor() {
        return new Trace(
                "trace-1",
                Duration.ofMillis(318),
                new ModelCall("test-model", 412, 18, Duration.ofMillis(300)),
                new Cost(new BigDecimal("0.000502"), "USD"));
    }

    @Test
    void theAnswerIsWhatTheResidentWillRead() {
        var response = TurnResponse.of(outcomeWith(free()), renderer);

        assertThat(response.reply()).isEqualTo("Para seguir necesito el número de reclamo.");
        assertThat(response.decision()).isEqualTo("AskFor [CLAIM_NUMBER]");
        assertThat(response.messageId()).isEqualTo("trace-1");
    }

    @Test
    void whatWasUnderstoodTravelsWithIt() {
        var intent = TurnResponse.of(outcomeWith(free()), renderer).intent();

        assertThat(intent.domain()).isEqualTo("RECLAMOS");
        assertThat(intent.action()).isEqualTo("CHECK_STATUS");
        assertThat(intent.confidence()).isEqualTo(0.85);
    }

    @Test
    void whatIsKnownIsAListOfNamesAndNothingMore() {
        var conversation = TurnResponse.of(outcomeWith(free()), renderer).conversation();

        assertThat(conversation.turn()).isEqualTo(1);
        assertThat(conversation.known()).containsExactly("DNI");
        assertThat(conversation.awaiting()).containsExactly("CLAIM_NUMBER");
    }

    @Test
    void aTurnThatReachedNoModelReportsNoUsage() {
        assertThat(TurnResponse.of(outcomeWith(free()), renderer).usage()).isNull();
    }

    @Test
    void aTurnThatDidReportsWhatItSpent() {
        var usage = TurnResponse.of(outcomeWith(paidFor()), renderer).usage();

        assertThat(usage).isNotNull();
        assertThat(usage.model()).isEqualTo("test-model");
        assertThat(usage.inputTokens()).isEqualTo(412);
        assertThat(usage.outputTokens()).isEqualTo(18);
        assertThat(usage.currency()).isEqualTo("USD");
        assertThat(usage.tookMillis()).isEqualTo(318);
    }

    @Test
    void theCostIsAStringSoNobodyRoundsIt() {
        // 0.000502 through a JSON number and back is a number somebody's parser has had
        // an opinion about. A total built out of those is not a total.
        assertThat(TurnResponse.of(outcomeWith(paidFor()), renderer).usage().cost()).isEqualTo("0.000502");
    }
}
