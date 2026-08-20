package com.municipality.agent.message;

import java.time.Instant;
import java.util.List;

/**
 * A message exactly as it arrived, before anything has been read into it.
 *
 * <p>The compact constructor below runs before the fields are assigned. It is
 * where the record refuses to exist in a shape the rest of the system would have
 * to defend itself against, and where the content list becomes a copy nobody
 * outside can mutate afterwards.
 *
 * <p>Every rule is one line, and every rejection is an {@link IllegalArgumentException}
 * — missing and unusable get the same answer. The point is that a reader scans one
 * shape down the constructor instead of three.
 *
 * @param traceId   follows this message through every step, for logs
 * @param userId    who sent it
 * @param timestamp when they sent it
 * @param contents  at least one piece of content
 */
public record IncomingMessage(String traceId, String userId, Instant timestamp, List<MessageContent> contents) {

    public IncomingMessage {
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId must not be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId must not be blank");
        if (timestamp == null) throw new IllegalArgumentException("timestamp is required");
        if (contents == null || contents.isEmpty()) throw new IllegalArgumentException("contents must not be empty");

        // Reassigning the parameter is what actually gets stored. Without this,
        // whoever handed us the list could keep adding to it behind our back.
        contents = List.copyOf(contents);
    }
}
