package com.municipality.agent.persistence;

import com.municipality.agent.AgentProperties;
import com.municipality.agent.conversation.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly delete, against the real schema, for both of the things that accumulate.
 *
 * <p>The clock is handed in rather than read, so "thirty-one days later" is a line in a
 * test instead of a suite that only passes next month.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConversations.class, JpaReceipts.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NightlySweepTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");
    private static final AgentProperties KEPT_FOR_A_MONTH = new AgentProperties(
            Duration.ofMinutes(30), AgentProperties.Store.JPA, Duration.ofDays(30), Duration.ofDays(2),
            "0 0 3 * * *", "");

    @Autowired
    private ConversationRows rows;

    @Autowired
    private ReceiptRows receipts;

    @Autowired
    private JpaReceipts answered;

    @Autowired
    private JpaConversations conversations;

    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void aConversationOnTheStrokeOfNoon() {
        inItsOwnTransaction(rows::deleteAll);
        inItsOwnTransaction(receipts::deleteAll);

        conversations.save(Conversation.startedBy("user-1", NOON).after(null, NOON));
        answered.remember(new com.municipality.agent.delivery.Receipt("wamid.1", "user-1", NOON, "{}"));
    }

    /** A delete is a write, and this slice does not open a transaction for one. */
    private void sweepAt(Instant now) {
        var sweep = new NightlySweep(rows, receipts, KEPT_FOR_A_MONTH, Clock.fixed(now, ZoneOffset.UTC));

        inItsOwnTransaction(sweep::sweep);
    }

    private void inItsOwnTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    @Test
    void aConversationOlderThanItIsKeptForIsDeleted() {
        sweepAt(NOON.plus(Duration.ofDays(31)));

        assertThat(rows.findById("user-1")).isEmpty();
    }

    @Test
    void aConversationInsideItsTimeIsLeftAlone() {
        sweepAt(NOON.plus(Duration.ofDays(29)));

        assertThat(rows.findById("user-1")).isPresent();
    }

    @Test
    void sweepingAnEmptyTableIsNotAnEvent() {
        inItsOwnTransaction(rows::deleteAll);
        inItsOwnTransaction(receipts::deleteAll);

        sweepAt(NOON.plus(Duration.ofDays(365)));

        assertThat(rows.count()).isZero();
    }

    @Test
    void aReceiptGoesLongBeforeTheConversationDoes() {
        // Nobody redelivers a two-day-old message. After that the row is a record of who
        // wrote in and when, which is the kind of thing to stop keeping.
        sweepAt(NOON.plus(Duration.ofDays(3)));

        assertThat(receipts.findById("wamid.1")).isEmpty();
        assertThat(rows.findById("user-1")).isPresent();
    }
}
