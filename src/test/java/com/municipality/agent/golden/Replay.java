package com.municipality.agent.golden;

import com.municipality.agent.Agent;
import com.municipality.agent.Outcome;
import com.municipality.agent.console.DecisionRenderer;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.observability.ModelCall;
import com.municipality.agent.router.Classification;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import com.municipality.agent.router.Intent;
import com.municipality.agent.router.KeywordClassifier;
import com.municipality.agent.support.Agents;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Runs a script and writes down everything that happened.
 *
 * <p>What comes out is the file that goes back in: the {@code you} lines are the script,
 * and the rest is the agent answering. That is the whole trick of these tests — the
 * expected output is never written by hand, so it cannot be quietly loosened, and a change
 * in behaviour shows up as a diff of a conversation rather than as a failed assertion
 * about a field.
 *
 * <p>Nothing here is allowed to vary between runs. Message ids come off a counter rather
 * than a random source, the clock moves by whole minutes, and how long a turn took is not
 * written down at all. A golden file that changes when nothing changed is a golden file
 * nobody reads.
 */
final class Replay {

    /** Where the conversation starts, so every transcript reads the same. */
    private static final Instant OPENING = Instant.parse("2026-08-24T10:00:00Z");

    private static final Duration BETWEEN_TURNS = Duration.ofMinutes(1);

    private static final String RESIDENT = "+5493415551234";

    private static final String NOTHING = "-";

    private final DecisionRenderer renderer = new DecisionRenderer();

    private Replay() {}

    static String of(Script script) {
        return new Replay().run(script);
    }

    private String run(Script script) {
        var transcript = new StringBuilder();

        withoutTrailingBlanks(script.preamble()).forEach(line -> transcript.append(line).append('\n'));

        Agent agent = Agents.around(classifierFor(script.classifier()), new InMemoryConversations());
        Instant now = OPENING;
        int sent = 0;

        for (Script.Step step : script.steps()) {
            switch (step) {
                case Script.Step.Waits(String written, Duration howLong) -> {
                    now = now.plus(howLong);
                    transcript.append('\n').append(Script.WAIT).append("      ").append(written).append('\n');
                }
                case Script.Step.Says(String line, var contents) -> {
                    now = now.plus(BETWEEN_TURNS);
                    sent++;

                    var message = new IncomingMessage(idOf(sent), RESIDENT, now, contents);

                    transcript.append('\n').append(Script.YOU).append("       ").append(line).append('\n');
                    write(transcript, agent.handle(message));
                }
            }
        }

        return transcript.toString();
    }

    /** So that a file read and written again is the same file, down to the blank lines. */
    private static java.util.List<String> withoutTrailingBlanks(java.util.List<String> lines) {
        int last = lines.size();

        while (last > 0 && lines.get(last - 1).isBlank()) last--;

        return lines.subList(0, last);
    }

    /** Every field a reader would want, and not one that changes between two identical runs. */
    private void write(StringBuilder transcript, Outcome outcome) {
        var intent = outcome.intent();
        var trace = outcome.trace();

        field(transcript, "intent", "%s / %s (%.2f)".formatted(
                intent.domain(), intent.action(), intent.confidence()));
        field(transcript, "decision", renderer.summary(outcome.decision()));
        field(transcript, "given", names(outcome.given().keySet()));
        field(transcript, "known", names(outcome.conversation().known().keySet()));
        field(transcript, "awaiting", names(outcome.conversation().expecting()));
        field(transcript, "turn", String.valueOf(outcome.conversation().turns()));

        if (trace.reachedAModel()) {
            ModelCall call = trace.call();
            field(transcript, "cost", "%s %s (%d in / %d out, %s)".formatted(
                    trace.cost().currency(), trace.cost().amount().toPlainString(),
                    call.inputTokens(), call.outputTokens(), call.model()));
        }

        field(transcript, "bot", renderer.reply(outcome.decision()));
    }

    private static void field(StringBuilder transcript, String name, String value) {
        transcript.append("  ").append(name).append(" ".repeat(Math.max(1, 10 - name.length())))
                .append(value).append('\n');
    }

    /** Names only. A transcript is a file in a repository; a document number is not. */
    private static String names(Collection<EntityType> entities) {
        var sorted = new TreeSet<String>();
        entities.forEach(entity -> sorted.add(entity.name()));

        return sorted.isEmpty() ? NOTHING : String.join(", ", sorted);
    }

    /** Counted, not random: a transcript that changes between runs is not a transcript. */
    private static String idOf(int sent) {
        return UUID.nameUUIDFromBytes(("golden-" + sent).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    private static com.municipality.agent.router.Classifier classifierFor(Script.Classifier which) {
        return switch (which) {
            case KEYWORDS -> new KeywordClassifier();
            case BILLING -> Agents.spending(412, 18);
            case UNREACHABLE -> message -> new Classification(
                    new Intent(Domain.UNKNOWN, com.municipality.agent.router.Action.INFORMATION, 0.0), null);
        };
    }
}
