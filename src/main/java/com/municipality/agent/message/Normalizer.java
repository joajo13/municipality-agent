package com.municipality.agent.message;

import org.jspecify.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * Collapses a message into the one piece of text the classifier reads.
 *
 * <p>It does not look at media itself. It asks a {@link MediaDescriber}, and when
 * that has nothing to say it falls back to announcing what arrived — {@code [audio]},
 * {@code [image]}. Those placeholders live here rather than in the describer on
 * purpose: they mean "nothing is known about this", which is the normaliser's
 * conclusion to draw, not the describer's answer.
 *
 * <p>A transcript goes in unmarked, because it is the resident's own words, only
 * spoken. What a model inferred from a photo or a file is bracketed, so the
 * classifier can tell inference apart from what a person actually wrote.
 */
public class Normalizer {

    /** Long enough for anything a resident writes, short enough not to flood a prompt. */
    private static final int MAX_LENGTH = 500;

    private static final String ELLIPSIS = "...";
    private static final String NOTHING_WAS_SAID = "[empty]";

    private final MediaDescriber describer;

    public Normalizer(MediaDescriber describer) {
        if (describer == null) {
            throw new IllegalArgumentException("describer is required");
        }
        this.describer = describer;
    }

    public NormalizedMessage normalize(IncomingMessage message) {
        String text = message.contents().stream()
                .map(this::contributionOf)
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));

        String said = text.isEmpty() ? NOTHING_WAS_SAID : text;

        return new NormalizedMessage(message.traceId(), message.userId(), message.timestamp(), capped(said));
    }

    /**
     * No {@code default} branch: {@link MessageContent} is sealed, so the compiler
     * checks this covers every kind there is, and will stop compiling if one is added.
     */
    private String contributionOf(MessageContent content) {
        return switch (content) {
            case Text(var body) -> body;
            case Audio audio -> describer.describe(audio).map(String::strip).orElse("[audio]");
            case Image image -> contributionOf(image);
            case Document document -> contributionOf(document);
            case Location(var latitude, var longitude) -> "[location " + latitude + "," + longitude + "]";
            case ButtonReply(_, var title) -> title;
        };
    }

    private String contributionOf(Image image) {
        String caption = textOrNull(image.caption());
        String seen = textOrNull(describer.describe(image).orElse(null));

        if (seen == null) {
            return caption == null ? "[image]" : caption;
        }
        // The resident's own words first, then what the model made of the photo.
        return caption == null ? "[image: " + seen + "]" : caption + " [image: " + seen + "]";
    }

    private String contributionOf(Document document) {
        String filename = textOrNull(document.filename());
        String read = textOrNull(describer.describe(document).orElse(null));

        String label = filename == null ? "document" : "document " + filename;
        return read == null ? "[" + label + "]" : "[" + label + ": " + read + "]";
    }

    private static @Nullable String textOrNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String capped(String text) {
        if (text.length() <= MAX_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
