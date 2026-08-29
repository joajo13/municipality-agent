package com.municipality.agent.persistence;

import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.OpenQuestion;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.Intent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The row and the record, and the conversion between them.
 *
 * <p>Worth its own test rather than only being exercised through the store, because the
 * interesting cases are rows that should not exist: half a question, a list of nothing.
 * A row like that is going to be read one day, and reading it as if the agent had asked
 * something would put words in its mouth.
 */
class ConversationRowTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    private static final Intent CHECKING = new Intent(Domain.RECLAMOS, Action.CHECK_STATUS, 0.85);

    @Test
    void aWholeConversationSurvivesTheRoundTrip() {
        var conversation = Conversation.startedBy("user-1", NOON)
                .learned(Map.of(DNI, "20123456", CLAIM_NUMBER, "REC-2026-00412"))
                .after(new OpenQuestion(CHECKING, Set.of(CLAIM_NUMBER)), NOON);

        assertThat(ConversationRow.from(conversation).asConversation()).isEqualTo(conversation);
    }

    @Test
    void aConversationWaitingForNothingComesBackWaitingForNothing() {
        var conversation = Conversation.startedBy("user-1", NOON).after(null, NOON);

        assertThat(ConversationRow.from(conversation).asConversation().asked()).isNull();
    }

    @Test
    void aRowMovesOnToTheNextTurnInPlace() {
        var row = ConversationRow.from(Conversation.startedBy("user-1", NOON).after(null, NOON));

        var next = Conversation.startedBy("user-1", NOON)
                .learned(Map.of(DNI, "20123456"))
                .after(new OpenQuestion(CHECKING, Set.of(CLAIM_NUMBER)), NOON)
                .after(new OpenQuestion(CHECKING, Set.of(CLAIM_NUMBER)), NOON);

        row.replaceWith(next);

        assertThat(row.getTurns()).isEqualTo(2);
        assertThat(row.getUserId()).isEqualTo("user-1");
        assertThat(row.asConversation()).isEqualTo(next);
    }

    @Test
    void whatWasLearnedAndThenForgottenIsGoneFromTheRowToo() {
        var row = ConversationRow.from(
                Conversation.startedBy("user-1", NOON).learned(Map.of(DNI, "20123456")).after(null, NOON));

        row.replaceWith(Conversation.startedBy("user-1", NOON).after(null, NOON));

        assertThat(row.asConversation().known()).isEmpty();
    }
}
