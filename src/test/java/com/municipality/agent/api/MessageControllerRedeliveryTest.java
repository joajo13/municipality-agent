package com.municipality.agent.api;

import com.municipality.agent.Agent;
import com.municipality.agent.delivery.InMemoryReceipts;
import com.municipality.agent.delivery.Receipt;
import com.municipality.agent.delivery.Receipts;
import com.municipality.agent.Turns;
import com.municipality.agent.support.Agents;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The controller on its own, with the two stores standing in, for the three paths a
 * request through it can take that a happy one does not.
 *
 * <p>No Spring here. What is being tested is a decision the controller makes — whether
 * this message has already been answered — and that decision is four lines of Java that
 * do not need a servlet container to be wrong.
 */
class MessageControllerRedeliveryTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    /** A response as another instance would have written it, and stored, first. */
    private static final String ANSWER_FROM_THE_OTHER_INSTANCE =
            """
            {"messageId": "wamid.2",
             "reply": "lo que escribio el otro",
             "decision": "FallbackMenu",
             "intent": {"domain": "UNKNOWN", "action": "INFORMATION", "confidence": 0.0},
             "conversation": {"turn": 1, "known": [], "awaiting": []}}
            """;

    private final JsonMapper json = JsonMapper.builder().build();
    private final Turns turns = Agents.watched(agent());

    private static Agent agent() {
        return Agents.keyword();
    }

    private MessageController controllerOver(Receipts receipts) {
        return new MessageController(
                turns, receipts, new RateLimiter(100, Duration.ofMinutes(1)), json,
                Clock.fixed(NOON, ZoneOffset.UTC));
    }

    private static MessageRequest message(String id) {
        return new MessageRequest(id, "+5493415551234", NOON, List.of(new ContentRequest.TextContent("hola")));
    }

    @Test
    void aFreshMessageIsHandledAndSaidToBeFresh() {
        var response = controllerOver(new InMemoryReceipts()).handle(message("wamid.1"));

        assertThat(response.getHeaders().getFirst(MessageController.REPLAY)).isEqualTo("false");
        assertThat(response.getBody().conversation().turn()).isEqualTo(1);
    }

    @Test
    void aMessageWithNoIdIsGivenOne() {
        var response = controllerOver(new InMemoryReceipts())
                .handle(new MessageRequest(null, "+5493415551234", NOON,
                        List.of(new ContentRequest.TextContent("hola"))));

        assertThat(response.getBody().messageId()).isNotBlank();
    }

    @Test
    void whenAnotherDeliveryGotThereFirstItsAnswerIsTheAnswer() {
        // Two instances handled the same delivery at once. Only one receipt can be
        // written, and the one that lost has to hand back what the winner wrote --
        // two different answers to one message id is the thing all of this prevents.
        var receipts = new Receipts() {

            private final Receipts written = new InMemoryReceipts();
            private boolean claimed;

            @Override
            public Optional<Receipt> of(String messageId) {
                return claimed ? written.of(messageId) : Optional.empty();
            }

            @Override
            public void remember(Receipt receipt) {
                claimed = true;
                written.remember(new Receipt(receipt.messageId(), receipt.userId(), receipt.receivedAt(),
                        ANSWER_FROM_THE_OTHER_INSTANCE));

                throw new AlreadyHandled(receipt.messageId());
            }
        };

        var response = controllerOver(receipts).handle(message("wamid.2"));

        assertThat(response.getHeaders().getFirst(MessageController.REPLAY)).isEqualTo("true");
        assertThat(response.getBody().reply()).isEqualTo("lo que escribio el otro");
    }

    @Test
    void aStoredAnswerThatCannotBeReadIsAnsweredAgainRatherThanFailing() {
        // A receipt written by an older version of this service can be a shape this one
        // does not read. Failing a redelivery over that would be worse than answering it.
        var receipts = new InMemoryReceipts();
        receipts.remember(new Receipt("wamid.3", "+5493415551234", NOON, "not json at all"));

        var response = controllerOver(receipts).handle(message("wamid.3"));

        assertThat(response.getBody().reply()).isNotBlank();
    }
}
