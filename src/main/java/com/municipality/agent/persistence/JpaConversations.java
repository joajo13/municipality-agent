package com.municipality.agent.persistence;

import com.municipality.agent.conversation.ConcurrentTurn;
import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.Conversations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Conversations in a table, which is what makes more than one instance of this service
 * worth running.
 *
 * <p>The whole point of the class is the write, and it is guarded twice for two different
 * races. The turn check catches the ordinary one — a turn built on what the conversation
 * looked like two seconds ago, which has moved since. The row version catches the one
 * that is left: two writes that both read the same row and are both in flight, where
 * whichever arrives second would otherwise overwrite the first without either of them
 * noticing.
 *
 * <p>Both come back to the caller as the same {@link ConcurrentTurn}, because from up
 * there they are the same fact: this turn was built on a conversation that has moved on,
 * and something has to be done about the message rather than nothing.
 */
@Repository
@ConditionalOnProperty(name = "agent.store", havingValue = "jpa", matchIfMissing = true)
public class JpaConversations implements Conversations {

    private final ConversationRows rows;

    public JpaConversations(ConversationRows rows) {
        this.rows = rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> of(String userId) {
        return rows.findById(userId).map(ConversationRow::asConversation);
    }

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        String userId = conversation.userId();
        int expected = conversation.turns() - 1;

        ConversationRow stored = rows.findById(userId).orElse(null);

        if (stored == null) {
            if (expected != 0) throw new ConcurrentTurn(userId);

            return inserted(conversation);
        }

        if (stored.getTurns() != expected) throw new ConcurrentTurn(userId);

        stored.replaceWith(conversation);

        return flushed(conversation);
    }

    private Conversation inserted(Conversation conversation) {
        try {
            rows.saveAndFlush(ConversationRow.from(conversation));
        } catch (DataIntegrityViolationException somebodyGotThereFirst) {
            throw new ConcurrentTurn(conversation.userId());
        }

        return conversation;
    }

    private Conversation flushed(Conversation conversation) {
        try {
            rows.flush();
        } catch (OptimisticLockingFailureException somebodyElseWroteIt) {
            throw new ConcurrentTurn(conversation.userId());
        }

        return conversation;
    }
}
