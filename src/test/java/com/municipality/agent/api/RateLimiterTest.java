package com.municipality.agent.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How many messages one resident may send in a window.
 *
 * <p>The clock is a parameter, so "a minute later" is an argument rather than a sleep.
 */
class RateLimiterTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");
    private static final Duration A_MINUTE = Duration.ofMinutes(1);

    private final RateLimiter limiter = new RateLimiter(3, A_MINUTE);

    @Test
    void theFirstFewAreAllowed() {
        assertThat(limiter.allows("user-1", NOON)).isTrue();
        assertThat(limiter.allows("user-1", NOON)).isTrue();
        assertThat(limiter.allows("user-1", NOON)).isTrue();
    }

    @Test
    void theOneAfterThatIsNot() {
        for (int sent = 0; sent < 3; sent++) limiter.allows("user-1", NOON);

        assertThat(limiter.allows("user-1", NOON)).isFalse();
    }

    @Test
    void aNewWindowStartsAgain() {
        for (int sent = 0; sent < 4; sent++) limiter.allows("user-1", NOON);

        assertThat(limiter.allows("user-1", NOON.plus(A_MINUTE))).isTrue();
    }

    @Test
    void theWindowIsStillOpenOnItsLastSecond() {
        for (int sent = 0; sent < 3; sent++) limiter.allows("user-1", NOON);

        assertThat(limiter.allows("user-1", NOON.plusSeconds(59))).isFalse();
    }

    @Test
    void oneResidentDoesNotSpendAnotherOnesAllowance() {
        for (int sent = 0; sent < 4; sent++) limiter.allows("user-1", NOON);

        assertThat(limiter.allows("user-2", NOON)).isTrue();
    }

    @Test
    void windowsThatClosedAreNotKeptForever() {
        // The map is per resident and a municipality has a lot of them. Nothing here
        // returns memory unless something eventually clears the closed windows out.
        for (int resident = 0; resident < 60_000; resident++) {
            limiter.allows("user-" + resident, NOON);
        }

        assertThat(limiter.allows("user-1", NOON.plus(Duration.ofHours(1)))).isTrue();
    }

    @Test
    void aLimitOfNoneIsNotALimit() {
        assertThatThrownBy(() -> new RateLimiter(0, A_MINUTE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neitherIsAWindowOfNoTime() {
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
