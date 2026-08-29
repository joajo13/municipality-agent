package com.municipality.agent.message;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normaliser collapses everything that arrived in a message into the one piece
 * of text the classifier will read. Line breaks the resident typed survive; the
 * result is capped so a very long message cannot flood the prompt.
 *
 * <p>It does not look at media itself: it asks a {@link MediaDescriber}. Here that
 * collaborator is either {@link NoMediaDescriber}, standing for "nothing has
 * transcribed or looked at this yet", or a canned one standing in for the model
 * that arrives in step 6. No Spring context and no network in either case.
 */
class NormalizerTest {

    private static final Instant SENT_AT = Instant.parse("2026-08-19T14:30:00Z");

    private static final String VOICE_NOTE = "https://cdn.example/audio/1.ogg";
    private static final String PHOTO = "https://cdn.example/img/1.jpg";
    private static final String FILE = "https://cdn.example/doc/1.pdf";

    /**
     * The situation until a real model is wired in: media arrives undescribed.
     */
    private static final MediaDescriber NOTHING_DESCRIBED = new NoMediaDescriber();

    /**
     * Normalises a message whose media nobody has described, and returns just the text.
     */
    private String textOf(MessageContent... contents) {
        return textOf(NOTHING_DESCRIBED, contents);
    }

    private String textOf(MediaDescriber describer, MessageContent... contents) {
        var incoming = new IncomingMessage("trace-1", "user-1", SENT_AT, List.of(contents));
        return new Normalizer(describer).normalize(incoming).text();
    }

    /**
     * Stands in for the transcription and vision calls of step 6. A null means "no answer".
     */
    private record CannedDescriptions(String forAudio, String forImage, String forDocument) implements MediaDescriber {

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
        assertThat(textOf(new Text("quiero castrar a mi perro"))).isEqualTo("quiero castrar a mi perro");
    }

    // --- audio: a transcript is the resident talking -------------------------

    @Test
    void audioIsAPlaceholderWhileNobodyHasTranscribedIt() {
        assertThat(textOf(new Audio(VOICE_NOTE))).isEqualTo("[audio]");
    }

    @Test
    void aTranscriptIsTheResidentsOwnWordsAndGoesInUnmarked() {
        // Nothing brackets it: the resident said this, they just said it out loud.
        var said = "hola queria saber por la castracion de gatos";

        var text = textOf(transcribing(said), new Audio(VOICE_NOTE));

        assertThat(text).isEqualTo(said);
    }

    // --- image: a description is the model talking ---------------------------

    @Test
    void imageContributesItsCaption() {
        var photo = new Image(PHOTO, "mira el pozo de mi cuadra");

        assertThat(textOf(photo)).isEqualTo("mira el pozo de mi cuadra");
    }

    @Test
    void imageWithoutACaptionIsAPlaceholderWhileNobodyHasLookedAtIt() {
        assertThat(textOf(new Image(PHOTO, null))).isEqualTo("[imagen]");
    }

    @Test
    void imageWithABlankCaptionIsAPlaceholder() {
        assertThat(textOf(new Image(PHOTO, "   "))).isEqualTo("[imagen]");
    }

    @Test
    void whatTheModelSawIsMarkedAsSuchAndFollowsTheResidentsCaption() {
        // The classifier has to be able to tell apart what the person wrote
        // from what a model inferred. Hence the caption first, and the brackets.
        var photo = new Image(PHOTO, "mira el pozo de mi cuadra");

        var text = textOf(seeing("una calle con un pozo lleno de agua"), photo);

        assertThat(text).isEqualTo("mira el pozo de mi cuadra [imagen: una calle con un pozo lleno de agua]");
    }

    @Test
    void aDescribedImageWithNoCaptionIsJustTheDescription() {
        var photo = new Image(PHOTO, null);

        var text = textOf(seeing("una calle con un pozo lleno de agua"), photo);

        assertThat(text).isEqualTo("[imagen: una calle con un pozo lleno de agua]");
    }

    // --- document ------------------------------------------------------------

    @Test
    void documentContributesItsFilename() {
        var attached = new Document(FILE, "acta-de-nacimiento.pdf");

        assertThat(textOf(attached)).isEqualTo("[documento acta-de-nacimiento.pdf]");
    }

    @Test
    void documentWithoutAFilenameIsAPlainPlaceholder() {
        assertThat(textOf(new Document(FILE, null))).isEqualTo("[documento]");
    }

    @Test
    void whatTheDocumentSaysIsMarkedTheSameWayAsAnImage() {
        var attached = new Document(FILE, "constancia.pdf");

        var text = textOf(reading("domicilio a nombre de Juan Perez"), attached);

        assertThat(text).isEqualTo("[documento constancia.pdf: domicilio a nombre de Juan Perez]");
    }

    // --- content that never needs a model ------------------------------------

    @Test
    void locationBecomesItsCoordinates() {
        assertThat(textOf(new Location(-33.33, -60.21))).isEqualTo("[ubicación -33.33,-60.21]");
    }

    @Test
    void buttonReplyContributesTheTitleAndNotTheId() {
        // The id is routing metadata; the title is what the resident actually saw and tapped.
        var tapped = new ButtonReply("appointments.start", "Pedir turno");

        assertThat(textOf(tapped)).isEqualTo("Pedir turno");
    }

    // --- combining several contents ------------------------------------------

    @Test
    void severalContentsAreJoinedInTheOrderTheyArrived() {
        var text = textOf(new Audio(VOICE_NOTE), new Text("es para castracion"));

        assertThat(text).isEqualTo("[audio] es para castracion");
    }

    @Test
    void aContentThatContributesNothingDoesNotLeaveADoubleSpace() {
        var text = textOf(new Text("hola"), new Text("   "), new Text("chau"));

        assertThat(text).isEqualTo("hola chau");
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(textOf(new Text("   hola   "))).isEqualTo("hola");
    }

    @Test
    void thereIsAlwaysSomethingForTheClassifierToRead() {
        // Whatever arrives, and whether or not a model looked at it, the
        // classifier never receives a blank line.
        assertThat(textOf(new Audio(VOICE_NOTE))).isNotBlank();
        assertThat(textOf(new Location(-33.33, -60.21))).isNotBlank();
        assertThat(textOf(new Text("   "))).isNotBlank();
    }

    // --- line breaks -----------------------------------------------------------

    @Test
    void lineBreaksTheResidentTypedSurvive() {
        // People fill in a form by hand, one field per line. Flattening that to a
        // single line would cost the classifier the structure it is reading.
        var typed = "nombre: Juan Perez\nDNI: 30111222\ndomicilio: Sarmiento 450";

        assertThat(textOf(new Text(typed))).isEqualTo(typed);
    }

    @Test
    void aTextOfNothingButLineBreaksContributesNothing() {
        assertThat(textOf(new Text("\n\n  \n"), new Text("hola"))).isEqualTo("hola");
    }

    @Test
    void aMessageThatSaysNothingAtAllIsMarkedAsEmpty() {
        assertThat(textOf(new Text("   "))).isEqualTo("[sin texto]");
    }

    // --- length ----------------------------------------------------------------

    @Test
    void textIsCappedAtFiveHundredCharacters() {
        assertThat(textOf(new Text("a".repeat(600)))).hasSize(500);
    }

    @Test
    void aCappedTextSaysThatItWasCut() {
        assertThat(textOf(new Text("a".repeat(600)))).endsWith("...");
    }

    @Test
    void aTextThatExactlyFitsIsLeftAlone() {
        var exactlyFiveHundred = "a".repeat(500);

        assertThat(textOf(new Text(exactlyFiveHundred))).isEqualTo(exactlyFiveHundred);
    }

    @Test
    void theCapAppliesToEverythingJoinedTogetherAndNotToEachContent() {
        // Three contents of two hundred characters each still add up to six hundred.
        var text = textOf(new Text("a".repeat(200)), new Text("b".repeat(200)), new Text("c".repeat(200)));

        assertThat(text).hasSize(500);
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

    @Test
    void aNormaliserWithNothingToAskAboutMediaIsNotANormaliser() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Normalizer(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
