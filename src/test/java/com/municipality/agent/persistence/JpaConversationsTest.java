package com.municipality.agent.persistence;

import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.ConversationsContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;

import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The store as it actually runs: a real schema, built by the real migrations, on a real
 * database engine.
 *
 * <p>{@code NOT_SUPPORTED} turns off the transaction the test slice would otherwise wrap
 * every test in. Without that, every write here would sit in one uncommitted transaction
 * and the conditional write — the only reason this class is worth its runtime — would
 * never be exercised against a row anybody else could see.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConversations.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaConversationsTest extends ConversationsContract {

    @Autowired
    private JpaConversations conversations;

    @Autowired
    private ConversationRows rows;

    @Autowired
    private PlatformTransactionManager transactions;

    @Override
    protected Conversations conversations() {
        return conversations;
    }

    @BeforeEach
    void empty() {
        inItsOwnTransaction(() -> {
            rows.deleteAll();
            return 0;
        });
    }

    @Test
    void whatTheResidentGaveIsRowsOfItsOwn() {
        // Not a blob on the conversation. These are the only values in the system that
        // identify a person, and they are worth being able to find and delete on their own.
        conversations.save(Conversation.startedBy("user-1", NOON).learned(Map.of(DNI, "20123456")).after(null, NOON));

        assertThat(rows.findById("user-1")).get().extracting(ConversationRow::getTurns).isEqualTo(1);
        assertThat(conversations.of("user-1")).get().extracting(Conversation::known).isEqualTo(Map.of(DNI, "20123456"));
    }

    @Test
    void conversationsNobodyCameBackToAreDeletedOutright() {
        conversations.save(Conversation.startedBy("user-1", NOON).learned(Map.of(DNI, "20123456")).after(null, NOON));

        int forgotten = deleteOlderThan(NOON.plus(Duration.ofDays(30)));

        assertThat(forgotten).isEqualTo(1);
        assertThat(conversations.of("user-1")).isEmpty();
    }

    @Test
    void conversationsStillWithinTheirTimeAreLeftAlone() {
        conversations.save(Conversation.startedBy("user-1", NOON).after(null, NOON));

        assertThat(deleteOlderThan(NOON.minus(Duration.ofDays(1)))).isZero();
        assertThat(conversations.of("user-1")).isPresent();
    }

    private int deleteOlderThan(java.time.Instant before) {
        return inItsOwnTransaction(() -> rows.deleteOlderThan(before));
    }

    /** A sweep is a write, and writes need a transaction that this slice is not providing. */
    private int inItsOwnTransaction(java.util.function.Supplier<Integer> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }
}
