package com.municipality.agent;

import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.message.NormalizedMessage;
import com.municipality.agent.observability.Cost;
import com.municipality.agent.observability.Trace;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Everything one turn produced.
 *
 * <p>Half of an outcome exists to be read by somebody working out why the agent said what
 * it said, so a half-built one is worse than none: it looks like an answer and explains
 * nothing.
 */
class OutcomeTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    private static final NormalizedMessage MESSAGE = new NormalizedMessage("trace-1", "user-1", NOON, "hola");
    private static final Intent INTENT = new Intent(Domain.UNKNOWN, Action.INFORMATION, 0.0);
    private static final Conversation CONVERSATION = Conversation.startedBy("user-1", NOON);
    private static final Trace TRACE = new Trace("trace-1", Duration.ofMillis(2), null, Cost.nothing("USD"));

    private static Outcome outcome(
            NormalizedMessage message,
            Intent intent,
            com.municipality.agent.policy.Decision decision,
            Map<EntityType, String> given,
            Conversation conversation,
            Trace trace) {

        return new Outcome(message, intent, decision, given, conversation, trace);
    }

    @Test
    void everyPartOfATurnIsRequired() {
        assertThatThrownBy(() -> outcome(null, INTENT, new FallbackMenu(), Map.of(), CONVERSATION, TRACE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outcome(MESSAGE, null, new FallbackMenu(), Map.of(), CONVERSATION, TRACE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outcome(MESSAGE, INTENT, null, Map.of(), CONVERSATION, TRACE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outcome(MESSAGE, INTENT, new FallbackMenu(), null, CONVERSATION, TRACE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outcome(MESSAGE, INTENT, new FallbackMenu(), Map.of(), null, TRACE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outcome(MESSAGE, INTENT, new FallbackMenu(), Map.of(), CONVERSATION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whatTheMessageGaveCannotBeChangedAfterwards() {
        var given = new HashMap<>(Map.of(EntityType.DNI, "20123456"));
        var outcome = outcome(MESSAGE, INTENT, new FallbackMenu(), given, CONVERSATION, TRACE);

        given.clear();

        assertThat(outcome.given()).containsEntry(EntityType.DNI, "20123456");
    }
}
