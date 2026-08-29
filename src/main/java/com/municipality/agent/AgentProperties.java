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
 * @param keepReceiptsFor how long an answered message is remembered so that a redelivery
 *                    of it gets the same answer. No provider redelivers a two-day-old
 *                    message; past that the row is a record of who wrote in and when.
 * @param sweepCron   when the deleting runs. Nightly, out of the way of the day.
 * @param pseudonymSecret what resident ids are named after in logs and traces. Set it and
 *                    the same resident reads as the same name across restarts and across
 *                    instances; leave it and one is generated per run, which is safe and
 *                    useless for anything older than the run.
 */
@ConfigurationProperties("agent")
public record AgentProperties(
        @DefaultValue("30m") Duration idleTimeout,
        @DefaultValue("jpa") Store store,
        @DefaultValue("30d") Duration retainFor,
        @DefaultValue("2d") Duration keepReceiptsFor,
        @DefaultValue("0 0 3 * * *") String sweepCron,
        @DefaultValue("") String pseudonymSecret) {

    /** Where conversations are kept. The names are the values {@code agent.store} takes. */
    public enum Store {
        JPA,
        MEMORY
    }
}
