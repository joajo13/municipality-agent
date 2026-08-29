package com.municipality.agent.support;

import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.message.MessageContent;
import com.municipality.agent.message.Text;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Messages as they would have arrived, for tests that care about what is in them rather
 * than where they came from.
 *
 * <p>The clock is the message's own timestamp, and it moves a minute at a time. Nothing
 * in the agent reads a wall clock, so a conversation of thirty turns runs in whatever
 * time the test takes and still ages exactly thirty minutes.
 */
public final class Messages {

    public static final Instant FIRST_SENT_AT = Instant.parse("2026-08-24T10:00:00Z");
    public static final Duration BETWEEN_TURNS = Duration.ofMinutes(1);

    private Messages() {}

    public static IncomingMessage from(String userId, String typed, Instant at) {
        return of(userId, at, new Text(typed));
    }

    public static IncomingMessage of(String userId, Instant at, MessageContent... contents) {
        return new IncomingMessage(UUID.randomUUID().toString(), userId, at, List.of(contents));
    }
}
