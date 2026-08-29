package com.municipality.agent.persistence;

import com.municipality.agent.conversation.ConcurrentTurn;
import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.delivery.Receipt;
import com.municipality.agent.delivery.Receipts;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two failures that only happen when two instances write at once.
 *
 * <p>They cannot be produced by writing twice from one thread against one database, which
 * is why the repository is a stand-in here: what is being tested is not the database's
 * behaviour but this code's reading of it. Both come back to the caller as one fact —
 * this turn was built on something that has moved — because from up there they are.
 */
class JpaStoresUnderRaceTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void twoInstancesInsertingTheSameConversationLeaveOneLoser() {
        ConversationRows rows = mock(ConversationRows.class);
        when(rows.findById(anyString())).thenReturn(Optional.empty());
        when(rows.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        var conversations = new JpaConversations(rows);
        var first = Conversation.startedBy("user-1", NOON).after(null, NOON);

        assertThatThrownBy(() -> conversations.save(first))
                .isInstanceOf(ConcurrentTurn.class)
                .hasMessageContaining("user-1");
    }

    @Test
    void twoInstancesUpdatingTheSameRowLeaveOneLoser() {
        ConversationRows rows = mock(ConversationRows.class);
        var stored = ConversationRow.from(Conversation.startedBy("user-1", NOON).after(null, NOON));

        when(rows.findById("user-1")).thenReturn(Optional.of(stored));
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("row moved")).when(rows).flush();

        var conversations = new JpaConversations(rows);
        var second = Conversation.startedBy("user-1", NOON).after(null, NOON).after(null, NOON);

        assertThatThrownBy(() -> conversations.save(second)).isInstanceOf(ConcurrentTurn.class);
    }

    @Test
    void twoDeliveriesOfOneMessageLeaveOneLoser() {
        ReceiptRows rows = mock(ReceiptRows.class);
        when(rows.existsById(anyString())).thenReturn(false);
        when(rows.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        var receipts = new JpaReceipts(rows);

        assertThatThrownBy(() -> receipts.remember(new Receipt("wamid.1", "user-1", NOON, "{}")))
                .isInstanceOf(Receipts.AlreadyHandled.class);
    }
}
