package com.municipality.agent.golden;

import com.municipality.agent.policy.Answer;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.policy.Handoff;
import com.municipality.agent.policy.StartFlow;
import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whole conversations, written down.
 *
 * <p>Every other test in this repository asks one question about one part. These ask the
 * only question a resident would: if I say this, and then this, and then this, what
 * happens? The answer is a file, and the file is the test — nobody wrote the expected
 * output by hand, so nobody can quietly loosen it, and a change in behaviour arrives as a
 * diff of a conversation rather than as a red assertion about a field.
 *
 * <p>To accept a deliberate change, run with {@code -Dgolden.update=true} and read the
 * diff before committing it. That diff is the review.
 */
class GoldenConversationTest {

    private static final Path TRANSCRIPTS = Path.of("src/test/resources/golden");

    /** Rewrites the transcripts instead of checking them. Never on in a build that matters. */
    private static final boolean ACCEPT_WHATEVER_HAPPENS = Boolean.getBoolean("golden.update");

    static Stream<Path> transcripts() throws IOException {
        try (var files = Files.list(TRANSCRIPTS)) {
            return files.filter(file -> file.toString().endsWith(".txt")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transcripts")
    void theConversationStillGoesThisWay(Path transcript) throws IOException {
        String written = Files.readString(transcript);
        String happened = Replay.of(Script.read(written));

        if (ACCEPT_WHATEVER_HAPPENS) {
            Files.writeString(transcript, happened);
            return;
        }

        assertThat(happened).isEqualTo(written);
    }

    /**
     * Every topic, every action, every outcome and every identifier appears in some
     * transcript.
     *
     * <p>Which makes adding one of those a failing test rather than an oversight: a new
     * domain with no conversation behind it is a domain nobody has watched the agent
     * handle end to end.
     */
    @Test
    void everythingTheAgentCanDoHappensInSomeConversation() {
        String everything = readEverything();

        for (Domain domain : Domain.values()) assertThat(everything).contains(domain.name());
        for (Action action : Action.values()) assertThat(everything).contains(action.name());
        for (EntityType entity : EntityType.values()) assertThat(everything).contains(entity.name());

        List<Class<?>> decisions = List.of(
                StartFlow.class, AskFor.class, Answer.class, FallbackMenu.class, Handoff.class);

        for (Class<?> decision : decisions) assertThat(everything).contains(decision.getSimpleName());
    }

    /** Every kind of thing a resident can send arrives in some transcript too. */
    @Test
    void everythingAResidentCanSendArrivesInSomeConversation() {
        String everything = readEverything();

        assertThat(everything).contains("audio:").contains("image:").contains("document:")
                .contains("location:").contains("button:");
    }

    private static String readEverything() {
        try (var files = Files.list(TRANSCRIPTS)) {
            return files.filter(file -> file.toString().endsWith(".txt"))
                    .map(GoldenConversationTest::read)
                    .reduce("", (all, one) -> all + "\n" + one);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
