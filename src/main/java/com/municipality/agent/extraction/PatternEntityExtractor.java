package com.municipality.agent.extraction;

import com.municipality.agent.router.EntityType;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads identifiers by their shape and by the word in front of them.
 *
 * <p>Patterns rather than a model, on purpose. An identifier is either well formed or it
 * is not, the rule fits on a screen, and a wrong answer here is not a misunderstanding —
 * it is a resident filed under somebody else's document number. That is not a decision to
 * hand to something that improvises.
 *
 * <p>Three rules, in order:
 *
 * <ol>
 *   <li>A value that says what it is — {@code REC-2026-00412} — is read as that, always.
 *   <li>A value with a label in front of it — "dni 20123456", "reclamo 4471" — is read as
 *       what the label says, if the shape agrees.
 *   <li>A bare value is read only when exactly one thing was expected and it fits. This
 *       is what makes a one-word answer work without turning every house number in every
 *       complaint into a document number.
 * </ol>
 */
public class PatternEntityExtractor implements EntityExtractor {

    /** A claim number that announces itself, whatever surrounds it. */
    private static final Pattern SELF_EVIDENT_CLAIM = Pattern.compile("\\brec-\\d{4}-\\d{1,6}\\b");

    /** A run of digits, once the grouping people write them with has been taken out. */
    private static final Pattern NUMBER = Pattern.compile("\\d{3,12}");

    /** The dot or space inside a grouped number: the one in 20.123.456, not the one ending a sentence. */
    private static final Pattern GROUPING = Pattern.compile("(?<=\\d)[.\\s](?=\\d{3}(?!\\d))");

    private static final Set<String> DNI_LABELS = Set.of("dni", "documento", "doc");
    private static final Set<String> CLAIM_LABELS = Set.of("reclamo", "expediente", "tramite", "ticket");

    /** How far back a label still counts as labelling a number: "mi dni es 20123456". */
    private static final int LABEL_REACH = 3;

    @Override
    public Map<EntityType, String> extract(String text, Set<EntityType> expected) {
        String reading = plain(text);
        Map<EntityType, String> found = new EnumMap<>(EntityType.class);

        SELF_EVIDENT_CLAIM.matcher(reading).results()
                .forEach(match -> found.putIfAbsent(EntityType.CLAIM_NUMBER, match.group().toUpperCase(Locale.ROOT)));

        List<String> words = List.of(reading.split("\\s+"));

        for (int at = 0; at < words.size(); at++) {
            String digits = numberIn(words.get(at));

            if (digits == null) continue;

            EntityType type = typeOf(digits, labelBefore(words, at), expected);

            if (type != null) found.putIfAbsent(type, digits);
        }

        return Map.copyOf(found);
    }

    /** What this number is, or {@code null} when nothing says. */
    private static @Nullable EntityType typeOf(String digits, @Nullable EntityType labelled, Set<EntityType> expected) {
        if (labelled != null) return fits(digits, labelled) ? labelled : null;

        // Nobody said what it is. The only reading that is not a guess is the one thing
        // the agent is standing there waiting for.
        if (expected.size() != 1) return null;

        EntityType only = expected.iterator().next();

        return fits(digits, only) ? only : null;
    }

    private static boolean fits(String digits, EntityType type) {
        return switch (type) {
            case DNI -> digits.length() >= 7 && digits.length() <= 8;
            case CLAIM_NUMBER -> digits.length() >= 4 && digits.length() <= 10;
        };
    }

    /** The nearest label within reach, or {@code null} when the number stands alone. */
    private static @Nullable EntityType labelBefore(List<String> words, int at) {
        for (int back = at - 1; back >= 0 && back >= at - LABEL_REACH; back--) {
            String word = words.get(back).replaceAll("[^a-z0-9]", "");

            if (DNI_LABELS.contains(word)) return EntityType.DNI;
            if (CLAIM_LABELS.contains(word)) return EntityType.CLAIM_NUMBER;
        }
        return null;
    }

    /** The number in a word, or {@code null} when there is none. */
    private static @Nullable String numberIn(String word) {
        var match = NUMBER.matcher(word);

        return match.find() ? match.group() : null;
    }

    /**
     * Lower case, no accents, and grouped numbers closed up so that 20.123.456 and
     * 20 123 456 read as the eight digits they are. Everything else keeps its
     * punctuation: the dashes are part of REC-2026-00412.
     */
    private static String plain(String text) {
        String flattened = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return GROUPING.matcher(flattened).replaceAll("");
    }
}
