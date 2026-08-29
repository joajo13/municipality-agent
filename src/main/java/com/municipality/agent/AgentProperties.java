package com.municipality.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Everything about the agent that an operator gets to set, in one place and with a
 * default for each. Nothing here has to be configured for the service to start.
 *
 * @param idleTimeout how long a conversation stays open with nothing said. Past it the
 *                    next message starts again from nothing: a document number given
 *                    yesterday is not permission to file something with it today.
 * @param store       where conversations are kept between turns. {@code jpa} is a table
 *                    and is what a second instance of this service needs; {@code memory}
 *                    is a map, for a run with nothing behind it.
 * @param retainFor   how long a conversation is kept before it is deleted outright. The
 *                    idle timeout stops old memory being used; this stops it being kept,
 *                    which is a different promise and the one a resident would ask about.
 * @param sweepCron   when the deleting runs. Nightly, out of the way of the day.
 */
@ConfigurationProperties("agent")
public record AgentProperties(
        @DefaultValue("30m") Duration idleTimeout,
        @DefaultValue("jpa") Store store,
        @DefaultValue("30d") Duration retainFor,
        @DefaultValue("0 0 3 * * *") String sweepCron) {

    /** Where conversations are kept. The names are the values {@code agent.store} takes. */
    public enum Store {
        JPA,
        MEMORY
    }
}
