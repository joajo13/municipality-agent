package com.municipality.agent.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A record hands you a constructor, accessors and equals/hashCode for free, but it
 * accepts whatever you pass: {@code new IncomingMessage(null, "  ", null, List.of())}
 * compiles and runs. These tests pin down the shapes an incoming message must refuse
 * to take, so that nothing downstream has to defend itself against them.
 *
 * <p>The place that enforces this is the <em>compact constructor</em> — declared with
 * no parameter list at all:
 *
 * <pre>{@code
 * public record IncomingMessage(String traceId, ..., List<MessageContent> contents) {
 *     public IncomingMessage {          // no parentheses, no parameters
 *         // parameters are in scope here, fields are not assigned yet;
 *         // reassigning a parameter is what actually gets stored
 *     }
 * }
 * }</pre>
 */
class IncomingMessageTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-19T14:30:00Z");

    /** Builds a valid message except for its contents, which is what most tests vary. */
    private IncomingMessage messageWith(List<MessageContent> contents) {
        return new IncomingMessage("trace-1", "user-1", SENT_AT, contents);
    }

    // --- what the compact constructor must refuse to build -------------------

    @Test
    void rejectsAMessageThatCarriesNoContentAtAll() {
        // A message with an empty content list has nothing to normalise and nothing to
        // classify. Rejecting it here means the normaliser can join the parts without
        // first asking whether there are any.
        assertThatThrownBy(() -> messageWith(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contents");
    }

    @Test
    void rejectsNullContents() {
        // Java convention: NullPointerException for something missing (that is what
        // Objects.requireNonNull throws), IllegalArgumentException for a value that is
        // present but unusable. Empty list -> IAE above; no list at all -> NPE here.
        assertThatThrownBy(() -> messageWith(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankTraceId() {
        // The trace id is how one conversation is followed across logs. A blank one is
        // worse than a missing one: it looks like a value and correlates nothing.
        assertThatThrownBy(() -> new IncomingMessage("  ", "user-1", SENT_AT, List.of(new Text("hola"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }

    @Test
    void rejectsABlankUserId() {
        // Same reasoning: the user id is what session state will eventually hang off.
        assertThatThrownBy(() -> new IncomingMessage("trace-1", "  ", SENT_AT, List.of(new Text("hola"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    // --- immutability, from both sides ---------------------------------------

    @Test
    void keepsItsOwnCopyOfTheContents() {
        // A record is only *shallowly* immutable: the `contents` field is final, so it
        // cannot be repointed, but the list it points at can still be mutated by whoever
        // handed it over. Without a copy, the line below would silently grow the message
        // after it was built.
        var mutable = new ArrayList<MessageContent>();
        mutable.add(new Text("hola"));
        var message = messageWith(mutable);

        mutable.add(new Text("colado despues de construir el mensaje"));

        assertThat(message.contents()).hasSize(1);
    }

    @Test
    void handsOutAListNobodyCanMutate() {
        // The mirror image of the test above: there, the leak is the caller keeping a
        // reference; here, it is the accessor handing one out. Two different bugs, even
        // though a single List.copyOf() in the compact constructor closes both.
        var message = messageWith(List.of(new Text("hola")));

        assertThatThrownBy(() -> message.contents().add(new Text("colado")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
