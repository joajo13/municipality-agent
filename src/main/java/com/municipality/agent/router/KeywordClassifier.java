package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stands in for a real model until step 5. It looks for words it was told about and
 * does nothing else: no context, no history, no reading between the lines.
 *
 * <p>Ambiguity is not its problem. "quiero un turno en el hospital y consultar mi
 * reclamo" mentions two topics and this will pick one — the first declared in
 * {@link Domain}. The point is to let the pipeline run end to end without a network
 * call, not to be right.
 *
 * <p>Note that the {@link Normalizer} imported here is {@code java.text.Normalizer},
 * which strips accents. It has nothing to do with the message normaliser of the same
 * name.
 */
public class KeywordClassifier implements Classifier {

    private static final Set<String> STATUS_WORDS = Set.of("estado", "consultar", "seguimiento");
    private static final Set<String> HANDOFF_WORDS = Set.of("persona", "humano", "operador", "hablar");
    private static final Set<String> QUESTION_WORDS =
            Set.of("horario", "requisitos", "cuanto", "donde", "cuando", "cuales");

    @Override
    public Classification classify(NormalizedMessage message) {
        Set<String> words = wordsOf(message.text());
        Domain domain = domainOf(words);

        // Either it knows what they are talking about or what they want done. Knowing
        // neither is the only case where it admits to knowing nothing.
        boolean recognisedSomething = domain != Domain.UNKNOWN || saysWhatToDo(words);

        return Classification.free(new Intent(domain, actionOf(words, domain), recognisedSomething ? 1.0 : 0.0));
    }

    /** The first domain declared that this message mentions, or {@link Domain#UNKNOWN}. */
    private static Domain domainOf(Set<String> words) {
        for (Domain domain : Domain.values()) {
            if (mentionsAny(words, wordsFor(domain))) return domain;
        }
        return Domain.UNKNOWN;
    }

    private static Action actionOf(Set<String> words, Domain domain) {
        if (mentionsAny(words, HANDOFF_WORDS)) return Action.HANDOFF;
        if (mentionsAny(words, STATUS_WORDS)) return Action.CHECK_STATUS;
        if (mentionsAny(words, QUESTION_WORDS)) return Action.INFORMATION;
        return defaultActionFor(domain);
    }

    private static boolean saysWhatToDo(Set<String> words) {
        return mentionsAny(words, HANDOFF_WORDS)
                || mentionsAny(words, STATUS_WORDS)
                || mentionsAny(words, QUESTION_WORDS);
    }

    /** With nothing else said: start the procedure, unless the domain has none to start. */
    private static Action defaultActionFor(Domain domain) {
        return switch (domain) {
            case SALUD, LICENCIAS, RECLAMOS -> Action.START_PROCEDURE;
            case SMALLTALK, UNKNOWN -> Action.INFORMATION;
        };
    }

    private static Set<String> wordsFor(Domain domain) {
        return switch (domain) {
            case SALUD -> Set.of("salud", "vacuna", "hospital", "medico");
            case LICENCIAS -> Set.of("licencia", "carnet", "registro", "conducir");
            case RECLAMOS -> Set.of("reclamo", "reclamar", "bache", "luminaria");
            case SMALLTALK -> Set.of("hola", "buenas", "gracias", "chau", "saludos");
            case UNKNOWN -> Set.of();
        };
    }

    private static boolean mentionsAny(Set<String> words, Set<String> wanted) {
        return words.stream().anyMatch(wanted::contains);
    }

    /**
     * Splits a line into the bare words it is made of: lower case, no accents, no
     * punctuation. Whole words only — otherwise "saludos" would match "salud" and a
     * goodbye would be routed to the health domain.
     */
    private static Set<String> wordsOf(String text) {
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        return Arrays.stream(stripped.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
    }
}
