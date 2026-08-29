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
 * Deletes conversations nobody has come back to.
 *
 * <p>This is not the idle timeout, and the two are worth keeping apart. The timeout is a
 * promise about answers: nothing said yesterday is used to decide anything today. This is
 * a promise about storage: a document number is not kept for longer than there is a
 * reason to keep it. A resident asking what is held about them is asking about the second
 * one.
 *
 * <p>Every instance runs this, and that is deliberate: the delete is a single statement
 * with a WHERE clause, so two of them running it at once do the same work twice and no
 * harm once. Leader election would be another moving part to buy nothing.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "agent.store", havingValue = "jpa", matchIfMissing = true)
public class ConversationSweep {

    private static final Logger log = LoggerFactory.getLogger(ConversationSweep.class);

    private final ConversationRows rows;
    private final AgentProperties properties;
    private final Clock clock;

    public ConversationSweep(ConversationRows rows, AgentProperties properties, Clock clock) {
        this.rows = rows;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${agent.sweep-cron:0 0 3 * * *}")
    @Transactional
    public void sweep() {
        Instant before = clock.instant().minus(properties.retainFor());
        int forgotten = rows.deleteOlderThan(before);

        if (forgotten > 0) log.info("Forgot {} conversations last touched before {}.", forgotten, before);
    }
}
