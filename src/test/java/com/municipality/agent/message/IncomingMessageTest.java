package com.municipality.agent.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A record is not just a bag of fields: the compact constructor is where an
 * incoming message gets to refuse to exist in a shape the rest of the system
 * would have to defend itself against.
 */
class IncomingMessageTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-19T14:30:00Z");

    private IncomingMessage messageWith(List<MessageContent> contents) {
        return new IncomingMessage("trace-1", "user-1", SENT_AT, contents);
    }

    @Test
    void rejectsAMessageThatCarriesNoContentAtAll() {
        assertThatThrownBy(() -> messageWith(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contents");
    }

    @Test
    void rejectsNullContents() {
        assertThatThrownBy(() -> messageWith(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankTraceId() {
        assertThatThrownBy(() -> new IncomingMessage("  ", "user-1", SENT_AT, List.of(new Text("hola"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }

    @Test
    void rejectsABlankUserId() {
        assertThatThrownBy(() -> new IncomingMessage("trace-1", "  ", SENT_AT, List.of(new Text("hola"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void keepsItsOwnCopyOfTheContents() {
        var mutable = new ArrayList<MessageContent>();
        mutable.add(new Text("hola"));
        var message = messageWith(mutable);

        mutable.add(new Text("colado despues de construir el mensaje"));

        assertThat(message.contents()).hasSize(1);
    }

    @Test
    void handsOutAListNobodyCanMutate() {
        var message = messageWith(List.of(new Text("hola")));

        assertThatThrownBy(() -> message.contents().add(new Text("colado")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
