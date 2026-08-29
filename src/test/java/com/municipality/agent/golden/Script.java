package com.municipality.agent.golden;

import com.municipality.agent.message.Audio;
import com.municipality.agent.message.ButtonReply;
import com.municipality.agent.message.Document;
import com.municipality.agent.message.Image;
import com.municipality.agent.message.Location;
import com.municipality.agent.message.MessageContent;
import com.municipality.agent.message.Text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A conversation as somebody wrote it down: the lines a resident sends, the gaps between
 * them, and a header saying what the agent is standing on while they do.
 *
 * <p>A script is read out of the same file the expected transcript is written to. The
 * {@code you} lines are the input; everything else in that file is what the agent said
 * back last time it was run, and is regenerated and compared. Nobody writes the expected
 * output by hand, and nobody can quietly stop checking it either.
 */
record Script(List<String> preamble, Classifier classifier, List<Step> steps) {

    /** The line that opens a message from the resident. */
    static final String YOU = "you";

    /** The line that says time passed. */
    static final String WAIT = "wait";

    private static final String CLASSIFIER = "# classifier:";

    /** What is answering, for the run of this script. */
    enum Classifier {

        /** The word list. Free, and sure of itself or not at all. */
        KEYWORDS,

        /** The word list, billed as if a model had answered: for the cost line. */
        BILLING,

        /** A model that cannot be reached. Every turn ends in the menu. */
        UNREACHABLE
    }

    /** One thing that happens: a message, or time going by. */
    sealed interface Step {

        record Says(String line, List<MessageContent> contents) implements Step {}

        record Waits(String written, Duration howLong) implements Step {}
    }

    static Script read(String file) {
        List<String> preamble = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        Classifier classifier = Classifier.KEYWORDS;

        for (String line : file.lines().toList()) {
            String trimmed = line.strip();

            if (trimmed.startsWith(CLASSIFIER)) {
                classifier = Classifier.valueOf(trimmed.substring(CLASSIFIER.length()).strip().toUpperCase());
                preamble.add(line);
            } else if (trimmed.startsWith("#") || (trimmed.isEmpty() && steps.isEmpty())) {
                preamble.add(line);
            } else if (trimmed.startsWith(YOU + " ")) {
                String said = trimmed.substring(YOU.length()).strip();
                steps.add(new Step.Says(said, List.of(contentOf(said))));
            } else if (trimmed.startsWith(WAIT + " ")) {
                String howLong = trimmed.substring(WAIT.length()).strip();
                steps.add(new Step.Waits(howLong, parse(howLong)));
            }
            // Everything else is output from the last run and is regenerated.
        }

        return new Script(List.copyOf(preamble), classifier, List.copyOf(steps));
    }

    /**
     * What the resident sent, from one line.
     *
     * <p>Anything with no prefix is typed text, which is almost everything. The prefixes
     * are how a script says a voice note or a shared pin arrived, so that a transcript can
     * cover the things a resident does that are not typing.
     */
    private static MessageContent contentOf(String said) {
        int mark = said.indexOf(':');
        String kind = mark < 0 ? "" : said.substring(0, mark);
        String rest = mark < 0 ? "" : said.substring(mark + 1);

        return switch (kind) {
            case "audio" -> new Audio(rest);
            case "image" -> new Image(before(rest), after(rest));
            case "document" -> new Document(before(rest), after(rest));
            case "button" -> new ButtonReply(before(rest), after(rest) == null ? "" : after(rest));
            case "location" -> new Location(
                    Double.parseDouble(before(rest).split(",")[0]),
                    Double.parseDouble(before(rest).split(",")[1]));
            default -> new Text(said);
        };
    }

    private static String before(String rest) {
        int bar = rest.indexOf('|');
        return bar < 0 ? rest : rest.substring(0, bar);
    }

    private static String after(String rest) {
        int bar = rest.indexOf('|');
        return bar < 0 ? null : rest.substring(bar + 1);
    }

    /** "8h", "45m", "30s" — enough to say what a gap in a conversation is. */
    private static Duration parse(String howLong) {
        long amount = Long.parseLong(howLong.substring(0, howLong.length() - 1));

        return switch (howLong.charAt(howLong.length() - 1)) {
            case 'h' -> Duration.ofHours(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 's' -> Duration.ofSeconds(amount);
            default -> throw new IllegalArgumentException("Not a length of time: " + howLong);
        };
    }
}
