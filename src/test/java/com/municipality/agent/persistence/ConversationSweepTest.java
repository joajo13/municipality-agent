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
 * The nightly delete, against the real schema.
 *
 * <p>The clock is handed in rather than read, so "thirty-one days later" is a line in a
 * test instead of a suite that only passes next month.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConversations.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationSweepTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");
    private static final AgentProperties KEPT_FOR_A_MONTH =
            new AgentProperties(Duration.ofMinutes(30), AgentProperties.Store.JPA, Duration.ofDays(30), "0 0 3 * * *");

    @Autowired
    private ConversationRows rows;

    @Autowired
    private JpaConversations conversations;

    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void aConversationOnTheStrokeOfNoon() {
        inItsOwnTransaction(rows::deleteAll);
        conversations.save(Conversation.startedBy("user-1", NOON).after(null, NOON));
    }

    /** A delete is a write, and this slice does not open a transaction for one. */
    private void sweepAt(Instant now) {
        var sweep = new ConversationSweep(rows, KEPT_FOR_A_MONTH, Clock.fixed(now, ZoneOffset.UTC));

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

        sweepAt(NOON.plus(Duration.ofDays(365)));

        assertThat(rows.count()).isZero();
    }
}
