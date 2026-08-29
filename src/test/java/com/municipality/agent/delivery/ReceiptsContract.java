package com.municipality.agent.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What every receipt store has to promise, written once and asked of both.
 *
 * <p>One promise, and everything the endpoint does about redelivery rests on it: a
 * message id written once cannot be written again.
 */
public abstract class ReceiptsContract {

    protected static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");

    protected abstract Receipts receipts();

    private static Receipt answered(String messageId) {
        return new Receipt(messageId, "user-1", NOON, "{\"reply\":\"hola\"}");
    }

    @Test
    void aMessageNobodyHasSeenIsNotRemembered() {
        assertThat(receipts().of("wamid.1")).isEmpty();
    }

    @Test
    void whatWasAnsweredComesBack() {
        receipts().remember(answered("wamid.1"));

        assertThat(receipts().of("wamid.1")).contains(answered("wamid.1"));
    }

    @Test
    void theSameMessageCannotBeAnsweredTwice() {
        receipts().remember(answered("wamid.1"));

        assertThatThrownBy(() -> receipts().remember(answered("wamid.1")))
                .isInstanceOf(Receipts.AlreadyHandled.class)
                .hasMessageContaining("wamid.1");
    }

    @Test
    void differentMessagesAreDifferentMessages() {
        receipts().remember(answered("wamid.1"));
        receipts().remember(answered("wamid.2"));

        assertThat(receipts().of("wamid.2")).isPresent();
    }
}
