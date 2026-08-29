package com.municipality.agent.conversation;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * What the agent remembers about one resident: what they have given it, what it is
 * waiting for, and how many turns have gone by.
 *
 * <p>It is a value, not a session. Nothing here is tied to a connection, a thread or a
 * machine — a turn loads one of these, replaces it with the next one, and hands it back.
 * Two instances of the service therefore behave the same way, which is the whole reason
 * the state is shaped like this.
 *
 * @param turns how many turns have been written. It doubles as the version: a turn is
 *              written against the number it read, so two turns racing on the same
 *              conversation cannot both win. See {@link Conversations#save}.
 * @param asked what the agent is waiting to be told, or {@code null} when it is waiting
 *              for nothing in particular
 */
public record Conversation(
        String userId,
        Map<EntityType, String> known,
        @Nullable OpenQuestion asked,
        int turns,
        Instant lastSeen) {

    public Conversation {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId must not be blank");
        if (known == null) throw new IllegalArgumentException("known is required");
        if (turns < 0) throw new IllegalArgumentException("turns must not be negative");
        if (lastSeen == null) throw new IllegalArgumentException("lastSeen is required");

        known = Map.copyOf(known);
    }

    /** A resident nobody has heard from before. */
    public static Conversation startedBy(String userId, Instant at) {
        return new Conversation(userId, Map.of(), null, 0, at);
    }

    /**
     * The same resident, remembered from nothing.
     *
     * <p>What was known and what was asked are gone; the turn count is not, because it
     * is also what says which write comes next. Forgetting somebody is a thing this
     * service does, not a thing that undoes the turns it already handled.
     */
    public Conversation forgotten(Instant at) {
        return new Conversation(userId, Map.of(), null, turns, at);
    }

    /**
     * Whether this is still the same conversation by the time the next message arrives.
     *
     * <p>Somebody who comes back the next morning is starting again, and answering them
     * out of yesterday's half-finished procedure would be worse than forgetting: a dni
     * given a day ago is not consent to file something with it today.
     */
    public boolean isOpenAt(Instant now, Duration idleFor) {
        return !now.isAfter(lastSeen.plus(idleFor));
    }

    /**
     * What the resident meant, read against what they were asked.
     *
     * <p>A message the classifier could make nothing of is the normal shape of an answer:
     * "20123456" is not a topic. When there is a question open, that message is the
     * answer to it, and the intent it was asked on behalf of is the one that carries on.
     *
     * <p>Two things are never read as answers. Anything the classifier did place is the
     * resident changing the subject, and they are allowed to. And asking for a person is
     * always asking for a person, no matter what was on the table a second ago.
     */
    public Intent read(Intent said) {
        if (asked == null) return said;
        if (said.action() == Action.HANDOFF) return said;
        if (said.domain() != Domain.UNKNOWN) return said;

        return asked.intent();
    }

    /** What the agent is waiting for, for whoever is reading a bare answer next. */
    public Set<EntityType> expecting() {
        return asked == null ? Set.of() : asked.missing();
    }

    /** The same conversation, plus whatever this message gave it. Newer wins. */
    public Conversation learned(Map<EntityType, String> given) {
        if (given.isEmpty()) return this;

        var merged = new EnumMap<EntityType, String>(EntityType.class);
        merged.putAll(known);
        merged.putAll(given);

        return new Conversation(userId, merged, asked, turns, lastSeen);
    }

    /** The conversation as it stands after a turn: one more written, and waiting for this. */
    public Conversation after(@Nullable OpenQuestion question, Instant at) {
        return new Conversation(userId, known, question, turns + 1, at);
    }
}
