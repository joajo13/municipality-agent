package com.municipality.agent.message;

import java.time.Instant;

/**
 * What the classifier reads: one message, collapsed into text. Identity and
 * timing travel along untouched, so a decision can still be traced back to the
 * message that caused it.
 */
public record NormalizedMessage(String traceId, String userId, Instant timestamp, String text) {
}
