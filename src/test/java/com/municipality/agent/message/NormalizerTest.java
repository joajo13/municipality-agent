package com.municipality.agent.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normaliser collapses everything that arrived in a message into the single
 * line of text the classifier will read. No Spring context here: it has no
 * collaborators, so it runs as plain Java.
 */
class NormalizerTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-19T14:30:00Z");

    private final Normalizer normalizer = new Normalizer();

    /** Normalises a message built from {@code contents} and returns just the text. */
    private String textOf(MessageContent... contents) {
        var incoming = new IncomingMessage("trace-1", "user-1", SENT_AT, List.of(contents));
        return normalizer.normalize(incoming).text();
    }

    // --- one test per variant of the sealed interface ------------------------

    @Test
    void textContributesItsBody() {
        assertThat(textOf(new Text("quiero castrar a mi perro")))
                .isEqualTo("quiero castrar a mi perro");
    }

    @Test
    void audioBecomesAPlaceholderUntilThereIsTranscription() {
        assertThat(textOf(new Audio("https://cdn.example/audio/1.ogg")))
                .isEqualTo("[audio]");
    }

    @Test
    void imageContributesItsCaption() {
        assertThat(textOf(new Image("https://cdn.example/img/1.jpg", "mira el pozo de mi cuadra")))
                .isEqualTo("mira el pozo de mi cuadra");
    }

    @Test
    void imageWithoutACaptionBecomesAPlaceholder() {
        assertThat(textOf(new Image("https://cdn.example/img/1.jpg", null)))
                .isEqualTo("[image]");
    }

    @Test
    void imageWithABlankCaptionBecomesAPlaceholder() {
        assertThat(textOf(new Image("https://cdn.example/img/1.jpg", "   ")))
                .isEqualTo("[image]");
    }

    @Test
    void documentContributesItsFilename() {
        assertThat(textOf(new Document("https://cdn.example/doc/1.pdf", "acta-de-nacimiento.pdf")))
                .isEqualTo("[document acta-de-nacimiento.pdf]");
    }

    @Test
    void documentWithoutAFilenameBecomesAPlainPlaceholder() {
        assertThat(textOf(new Document("https://cdn.example/doc/1.pdf", null)))
                .isEqualTo("[document]");
    }

    @Test
    void locationBecomesItsCoordinates() {
        assertThat(textOf(new Location(-33.33, -60.21)))
                .isEqualTo("[location -33.33,-60.21]");
    }

    @Test
    void buttonReplyContributesTheTitleAndNotTheId() {
        // The id is routing metadata; the title is what the resident actually saw and tapped.
        assertThat(textOf(new ButtonReply("appointments.start", "Pedir turno")))
                .isEqualTo("Pedir turno");
    }

    // --- combining several contents -----------------------------------------

    @Test
    void severalContentsAreJoinedInTheOrderTheyArrived() {
        assertThat(textOf(new Audio("https://cdn.example/audio/1.ogg"), new Text("es para castracion")))
                .isEqualTo("[audio] es para castracion");
    }

    @Test
    void aContentThatContributesNothingDoesNotLeaveADoubleSpace() {
        assertThat(textOf(new Text("hola"), new Text("   "), new Text("chau")))
                .isEqualTo("hola chau");
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(textOf(new Text("   hola   "))).isEqualTo("hola");
    }

    @Test
    void thereIsAlwaysSomethingForTheClassifierToRead() {
        // Whatever arrives, the classifier never receives a blank line.
        assertThat(textOf(new Audio("https://cdn.example/audio/1.ogg"))).isNotBlank();
        assertThat(textOf(new Location(-33.33, -60.21))).isNotBlank();
        assertThat(textOf(new Text("   "))).isNotBlank();
    }

    // --- everything that is not the text ------------------------------------

    @Test
    void identityAndTimestampAreCarriedOverUntouched() {
        var incoming = new IncomingMessage("trace-9", "user-9", SENT_AT, List.of(new Text("hola")));

        var normalized = normalizer.normalize(incoming);

        assertThat(normalized.traceId()).isEqualTo("trace-9");
        assertThat(normalized.userId()).isEqualTo("user-9");
        assertThat(normalized.timestamp()).isEqualTo(SENT_AT);
    }
}
