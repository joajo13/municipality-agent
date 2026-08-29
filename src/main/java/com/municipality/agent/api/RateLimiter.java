package com.municipality.agent.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many messages one resident may send in a minute.
 *
 * <p>Every message costs a model call, and a model call costs money. Without a limit, one
 * loop in one integration is a bill, and a bad afternoon is a bill nobody signed off. The
 * limit is per resident rather than per caller because the caller is the channel and is
 * always the same one.
 *
 * <p>This counts per instance, and that is an approximation: three instances behind a load
 * balancer allow three times this. It is deliberate. A shared counter means a round trip
 * to somewhere on every message, and the thing being defended against — a runaway loop —
 * is stopped just as well by a limit that is three times too generous as by an exact one.
 * The exact one belongs in the gateway, not here.
 */
public class RateLimiter {

    /** Windows kept before the old ones are cleared out. Big enough for a municipality. */
    private static final int MAX_TRACKED = 50_000;

    private final int allowance;
    private final Duration window;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public RateLimiter(int allowance, Duration window) {
        if (allowance < 1) throw new IllegalArgumentException("allowance must be at least 1");
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }

        this.allowance = allowance;
        this.window = window;
    }

    /** Whether this resident may send one more, and counts it if so. */
    public boolean allows(String userId, Instant now) {
        if (attempts.size() > MAX_TRACKED) forgetWindowsThatClosed(now);

        Attempts current = attempts.compute(userId, (id, existing) ->
                existing == null || existing.hasClosedBy(now, window) ? new Attempts(now) : existing);

        return current.countOneMore() <= allowance;
    }

    private void forgetWindowsThatClosed(Instant now) {
        attempts.values().removeIf(attempt -> attempt.hasClosedBy(now, window));
    }

    /** One window, and how much has been used of it. */
    private static final class Attempts {

        private final Instant opened;
        private final AtomicInteger used = new AtomicInteger();

        private Attempts(Instant opened) {
            this.opened = opened;
        }

        private boolean hasClosedBy(Instant now, Duration window) {
            return !now.isBefore(opened.plus(window));
        }

        private int countOneMore() {
            return used.incrementAndGet();
        }
    }
}
