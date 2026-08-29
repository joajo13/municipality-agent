package com.municipality.agent.console;

import com.municipality.agent.policy.Answer;
import com.municipality.agent.policy.AskFor;
import com.municipality.agent.policy.Decision;
import com.municipality.agent.policy.FallbackMenu;
import com.municipality.agent.policy.Handoff;
import com.municipality.agent.policy.StartFlow;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;

import java.util.stream.Collectors;

/**
 * Puts a decision into words.
 *
 * <p>This is the only class in the project that writes Spanish. The code and its names
 * are in English; what a resident reads is not, and the boundary between the two is
 * exactly here.
 *
 * <p>Both switches below have no {@code default}. A sixth kind of decision stops the
 * build in this file until somebody says what it sounds like — which is the point of
 * having sealed the interface in the first place.
 */
public class DecisionRenderer {

    /** What the resident reads. */
    public String reply(Decision decision) {
        return switch (decision) {
            case StartFlow flow -> "Listo, arranco el trámite de " + inSpanish(flow.domain()) + ".";
            case AskFor askFor -> "Para seguir necesito " + listed(askFor.missing()) + ".";
            // Not "¡Hola!": this is also what somebody who said thank you and goodbye
            // gets, and greeting them on the way out reads like nobody was listening.
            case Answer(Domain domain) when domain == Domain.SMALLTALK ->
                    "Estoy para ayudarte con salud, licencias y reclamos. ¿Qué necesitás?";
            case Answer answer -> "Te respondo sobre " + inSpanish(answer.domain()) + ".";
            case FallbackMenu() -> "No te entendí. Puedo ayudarte con: salud, licencias y reclamos.";
            case Handoff ignored -> "Te paso con una persona.";
        };
    }

    /** The technical line the trace shows, so a right answer can be told from a lucky one. */
    public String summary(Decision decision) {
        return switch (decision) {
            case StartFlow flow -> "StartFlow " + flow.domain() + " / " + flow.action();
            case AskFor askFor -> "AskFor " + askFor.missing();
            case Answer answer -> "Answer " + answer.domain();
            case FallbackMenu() -> "FallbackMenu";
            case Handoff handoff -> "Handoff " + handoff.domain();
        };
    }

    private static String listed(java.util.Set<EntityType> entities) {
        return entities.stream().map(DecisionRenderer::inSpanish).collect(Collectors.joining(" y "));
    }

    private static String inSpanish(EntityType entity) {
        return switch (entity) {
            case DNI -> "tu DNI";
            case CLAIM_NUMBER -> "el número de reclamo";
        };
    }

    private static String inSpanish(Domain domain) {
        return switch (domain) {
            case SALUD -> "salud";
            case LICENCIAS -> "licencias";
            case RECLAMOS -> "reclamos";
            case SMALLTALK -> "nada en particular";
            case UNKNOWN -> "algo que no reconocí";
        };
    }
}
