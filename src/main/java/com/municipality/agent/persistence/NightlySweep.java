package com.municipality.agent.persistence;

import com.municipality.agent.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Deletes what there is no longer a reason to keep.
 *
 * <p>This is not the idle timeout, and the two are worth keeping apart. The timeout is a
 * promise about answers: nothing said yesterday is used to decide anything today. This is
 * a promise about storage: a document number is not kept for longer than there is a
 * reason to keep it. A resident asking what is held about them is asking about the second
 * one.
 *
 * <p>Receipts go sooner and for a different reason. They exist so a provider redelivering
 * a message gets the answer it was already given; no provider redelivers a two-day-old
 * message, and after that the row is a record of who wrote in and when, which is exactly
 * what nobody should be keeping without a reason.
 *
 * <p>Every instance runs this, and that is deliberate: each delete is a single statement
 * with a WHERE clause, so two of them running it at once do the same work twice and no
 * harm once. Leader election would be another moving part to buy nothing.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "agent.store", havingValue = "jpa", matchIfMissing = true)
public class NightlySweep {

    private static final Logger log = LoggerFactory.getLogger(NightlySweep.class);

    private final ConversationRows conversations;
    private final ReceiptRows receipts;
    private final AgentProperties properties;
    private final Clock clock;

    public NightlySweep(
            ConversationRows conversations, ReceiptRows receipts, AgentProperties properties, Clock clock) {

        this.conversations = conversations;
        this.receipts = receipts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${agent.sweep-cron:0 0 3 * * *}")
    @Transactional
    public void sweep() {
        Instant now = clock.instant();

        forget("conversations", conversations.deleteOlderThan(now.minus(properties.retainFor())));
        forget("receipts", receipts.deleteOlderThan(now.minus(properties.keepReceiptsFor())));
    }

    private static void forget(String what, int deleted) {
        if (deleted > 0) log.info("Forgot {} {}.", deleted, what);
    }
}
