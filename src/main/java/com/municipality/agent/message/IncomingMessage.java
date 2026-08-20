package com.municipality.agent.message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A message exactly as it arrived, before anything has been read into it.
 *
 * <p>The compact constructor below runs before the fields are assigned. It is
 * where the record refuses to exist in a shape the rest of the system would have
 * to defend itself against, and where the content list becomes a copy nobody
 * outside can mutate afterwards.
 *
 * @param traceId   follows this message through every step, for logs
 * @param userId    who sent it
 * @param timestamp when they sent it
 * @param contents  at least one piece of content
 */
public record IncomingMessage(String traceId, String userId, Instant timestamp, List<MessageContent> contents) {

    public IncomingMessage {
        requireText(traceId, "traceId");
        requireText(userId, "userId");
        Objects.requireNonNull(timestamp, "timestamp is required");
        Objects.requireNonNull(contents, "contents is required");

        if (contents.isEmpty()) {
            throw new IllegalArgumentException("contents must carry at least one item");
        }
        // Reassigning the parameter is what actually gets stored. Without this,
        // whoever handed us the list could keep adding to it behind our back.
        contents = List.copyOf(contents);
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
