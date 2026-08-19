package com.municipality.agent.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normaliser collapses everything that arrived in a message into the single
 * line of text the classifier will read.
 *
 * <p>It does not look at media itself: it asks a {@link MediaDescriber}. Here that
 * collaborator is either {@link NoMediaDescriber}, standing for "nothing has
 * transcribed or looked at this yet", or a canned one standing in for the model
 * that arrives in step 6. No Spring context and no network in either case.
 */
class NormalizerTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-19T14:30:00Z");

    /** The situation until a real model is wired in: media arrives undescribed. */
    private static final MediaDescriber NOTHING_DESCRIBED = new NoMediaDescriber();

    /** Normalises a message whose media nobody has described, and returns just the text. */
    private String textOf(MessageContent... contents) {
        return textOf(NOTHING_DESCRIBED, contents);
    }

    private String textOf(MediaDescriber describer, MessageContent... contents) {
        var incoming = new IncomingMessage("trace-1", "user-1", SENT_AT, List.of(contents));
        return new Normalizer(describer).normalize(incoming).text();
    }

    /** Stands in for the transcription and vision calls of step 6. A null means "no answer". */
    private record CannedDescriptions(String forAudio, String forImage, String forDocument)
            implements MediaDescriber {

        @Override
        public Optional<String> describe(Audio audio) {
            return Optional.ofNullable(forAudio);
        }

        @Override
        public Optional<String> describe(Image image) {
            return Optional.ofNullable(forImage);
        }

        @Override
        public Optional<String> describe(Document document) {
            return Optional.ofNullable(forDocument);
        }
    }

    private static MediaDescriber transcribing(String transcript) {
        return new CannedDescriptions(transcript, null, null);
    }

    private static MediaDescriber seeing(String description) {
        return new CannedDescriptions(null, description, null);
    }

    private static MediaDescriber reading(String contents) {
        return new CannedDescriptions(null, null, contents);
    }

    // --- text ----------------------------------------------------------------

    @Test
    void textContributesItsBody() {
        assertThat(textOf(new Text("quiero castrar a mi perro")))
                .isEqualTo("quiero castrar a mi perro");
    }

    // --- audio: a transcript is the resident talking -------------------------

    @Test
    void audioIsAPlaceholderWhileNobodyHasTranscribedIt() {
        assertThat(textOf(new Audio("https://cdn.example/audio/1.ogg")))
                .isEqualTo("[audio]");
    }

    @Test
    void aTranscriptIsTheResidentsOwnWordsAndGoesInUnmarked() {
        // Nothing brackets it: the resident said this, they just said it out loud.
        assertThat(textOf(transcribing("hola queria saber por la castracion de gatos"),
                new Audio("https://cdn.example/audio/1.ogg")))
                .isEqualTo("hola queria saber por la castracion de gatos");
    }

    // --- image: a description is the model talking ---------------------------

    @Test
    void imageContributesItsCaption() {
        assertThat(textOf(new Image("https://cdn.example/img/1.jpg", "mira el pozo de mi cuadra")))
                .isEqualTo("mira el pozo de mi cuadra");
    }

    @Test
    void imageWithoutACaptionIsAPlaceholderWhileNobodyHasLookedAtIt() {
        assertThat(textOf(new Image("https://cdn.example/img/1.jpg", null)))
                .isEqualTo("[image]");
    }

    @Test
    void imageWithABlankCaptionIsAPlaceholder() {
        assertThat(textOf(new Image("https://cdn.example/img/1.jpg", "   ")))
                .isEqualTo("[image]");
    }

    @Test
    void whatTheModelSawIsMarkedAsSuchAndFollowsTheResidentsCaption() {
        // The classifier has to be able to tell apart what the person wrote
        // from what a model inferred. Hence the caption first, and the brackets.
        assertThat(textOf(seeing("una calle de asfalto con un pozo lleno de agua"),
                new Image("https://cdn.example/img/1.jpg", "mira el pozo de mi cuadra")))
                .isEqualTo("mira el pozo de mi cuadra [image: una calle de asfalto con un pozo lleno de agua]");
    }

    @Test
    void aDescribedImageWithNoCaptionIsJustTheDescription() {
        assertThat(textOf(seeing("una calle de asfalto con un pozo lleno de agua"),
                new Image("https://cdn.example/img/1.jpg", null)))
                .isEqualTo("[image: una calle de asfalto con un pozo lleno de agua]");
    }

    // --- document ------------------------------------------------------------

    @Test
    void documentContributesItsFilename() {
        assertThat(textOf(new Document("https://cdn.example/doc/1.pdf", "acta-de-nacimiento.pdf")))
                .isEqualTo("[document acta-de-nacimiento.pdf]");
    }

    @Test
    void documentWithoutAFilenameIsAPlainPlaceholder() {
        assertThat(textOf(new Document("https://cdn.example/doc/1.pdf", null)))
                .isEqualTo("[document]");
    }

    @Test
    void whatTheDocumentSaysIsMarkedTheSameWayAsAnImage() {
        assertThat(textOf(reading("constancia de domicilio a nombre de Juan Perez"),
                new Document("https://cdn.example/doc/1.pdf", "constancia.pdf")))
                .isEqualTo("[document constancia.pdf: constancia de domicilio a nombre de Juan Perez]");
    }

    // --- content that never needs a model ------------------------------------

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

    // --- combining several contents ------------------------------------------

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
        // Whatever arrives, and whether or not a model looked at it, the
        // classifier never receives a blank line.
        assertThat(textOf(new Audio("https://cdn.example/audio/1.ogg"))).isNotBlank();
        assertThat(textOf(new Location(-33.33, -60.21))).isNotBlank();
        assertThat(textOf(new Text("   "))).isNotBlank();
    }

    // --- everything that is not the text -------------------------------------

    @Test
    void identityAndTimestampAreCarriedOverUntouched() {
        var incoming = new IncomingMessage("trace-9", "user-9", SENT_AT, List.of(new Text("hola")));

        var normalized = new Normalizer(NOTHING_DESCRIBED).normalize(incoming);

        assertThat(normalized.traceId()).isEqualTo("trace-9");
        assertThat(normalized.userId()).isEqualTo("user-9");
        assertThat(normalized.timestamp()).isEqualTo(SENT_AT);
    }
}
