package com.municipality.agent.persistence;

import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.OpenQuestion;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One conversation as a row, and the only class in the system that knows a database
 * exists.
 *
 * <p>It is deliberately not the domain type. {@link Conversation} is a record with
 * behaviour on it and no no-argument constructor; this is a mutable bean with an
 * identity column, which is what JPA needs and what nothing else should have to put up
 * with. The two are kept apart by {@link #asConversation()} and {@link #from}, and that
 * conversion is the only place the shapes have to agree.
 *
 * <p>Enums are stored by name, never by ordinal. Reordering {@link Domain} would silently
 * re-file every stored conversation, and the ordering of that enum is already load-bearing
 * somewhere else.
 */
@Entity
@Table(name = "conversation")
public class ConversationRow {

    /** Between the names in {@code asked_missing}. */
    private static final String SEPARATOR = ",";

    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "turns", nullable = false)
    private int turns;

    /**
     * The row's own counter, kept by Hibernate and used in the WHERE clause of every
     * update. It is not {@code turns}: that one is the conversation's, it is reset by
     * nothing and read by the console, and a conversation that is forgotten and started
     * again carries it on. This one only ever answers "did anybody else write this row
     * since I read it".
     */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(name = "asked_domain", length = 32)
    private @Nullable Domain askedDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "asked_action", length = 32)
    private @Nullable Action askedAction;

    @Column(name = "asked_confidence")
    private @Nullable Double askedConfidence;

    @Column(name = "asked_missing", length = 255)
    private @Nullable String askedMissing;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "conversation_entity", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "entity_type", length = 32)
    @Column(name = "entity_value", nullable = false, length = 64)
    private Map<EntityType, String> known = new EnumMap<>(EntityType.class);

    /** For JPA, which builds one of these before it has anything to put in it. */
    protected ConversationRow() {}

    public static ConversationRow from(Conversation conversation) {
        var row = new ConversationRow();
        row.userId = conversation.userId();
        row.turns = conversation.turns();
        row.lastSeen = conversation.lastSeen();
        row.known.putAll(conversation.known());

        OpenQuestion asked = conversation.asked();

        if (asked != null) {
            row.askedDomain = asked.intent().domain();
            row.askedAction = asked.intent().action();
            row.askedConfidence = asked.intent().confidence();
            row.askedMissing = asked.missing().stream().map(Enum::name).collect(Collectors.joining(SEPARATOR));
        }

        return row;
    }

    /** Moves this row on to the next turn of the same conversation. */
    public void replaceWith(Conversation conversation) {
        var next = from(conversation);

        this.turns = next.turns;
        this.lastSeen = next.lastSeen;
        this.askedDomain = next.askedDomain;
        this.askedAction = next.askedAction;
        this.askedConfidence = next.askedConfidence;
        this.askedMissing = next.askedMissing;

        // Replaced in place: Hibernate is watching this collection, and handing it a new
        // one would only make it work out what changed the hard way.
        this.known.clear();
        this.known.putAll(next.known);
    }

    public Conversation asConversation() {
        return new Conversation(userId, known, askedQuestion(), turns, lastSeen);
    }

    /**
     * The four asked_* columns are one thing or nothing at all. A row with a domain and
     * no confidence is not a half-remembered question, it is a broken row, and reading it
     * as if the agent had asked something would put words in its mouth.
     */
    private @Nullable OpenQuestion askedQuestion() {
        if (askedDomain == null || askedAction == null || askedConfidence == null || askedMissing == null) {
            return null;
        }

        Set<EntityType> missing = Arrays.stream(askedMissing.split(SEPARATOR))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(EntityType::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (missing.isEmpty()) return null;

        return new OpenQuestion(new Intent(askedDomain, askedAction, askedConfidence), missing);
    }

    public String getUserId() {
        return userId;
    }

    public int getTurns() {
        return turns;
    }
}
