package com.municipality.agent.delivery;

import java.time.Instant;

/**
 * A message that was handled, and the answer it was given.
 *
 * <p>Messaging providers redeliver. A timeout on their side, a retry policy, a network
 * that dropped the acknowledgement rather than the request — and the same message arrives
 * again, sometimes minutes later. Without something like this, a resident who sent one
 * message gets two turns, and a procedure that should have started once starts twice.
 *
 * @param response what was sent back the first time, kept whole so the second delivery
 *                 gets the same answer rather than a fresh one
 */
public record Receipt(String messageId, String userId, Instant receivedAt, String response) {

    public Receipt {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("messageId must not be blank");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId must not be blank");
        if (receivedAt == null) throw new IllegalArgumentException("receivedAt is required");
        if (response == null) throw new IllegalArgumentException("response is required");
    }
}
