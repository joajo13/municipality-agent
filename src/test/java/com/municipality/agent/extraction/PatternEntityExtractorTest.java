package com.municipality.agent.extraction;

import com.municipality.agent.router.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading identifiers out of what somebody wrote.
 *
 * <p>Half of these are about what must *not* be read. A house number in a complaint, a
 * year, a phone number: taking any of those for a document number files a resident under
 * somebody else's identity, which is the one mistake in this system that a later turn
 * cannot undo.
 */
class PatternEntityExtractorTest {

    private final EntityExtractor extractor = new PatternEntityExtractor();

    private java.util.Map<EntityType, String> from(String text) {
        return extractor.extract(text, Set.of());
    }

    private java.util.Map<EntityType, String> from(String text, EntityType expected) {
        return extractor.extract(text, Set.of(expected));
    }

    // --- values that say what they are ---------------------------------------

    @Test
    void aClaimNumberInItsOwnFormatIsReadWhereverItAppears() {
        assertThat(from("hola, mi reclamo es el REC-2026-00412 gracias"))
                .containsEntry(CLAIM_NUMBER, "REC-2026-00412");
    }

    @Test
    void aClaimNumberIsReadInUpperCaseHoweverItWasTyped() {
        assertThat(from("rec-2026-00412")).containsEntry(CLAIM_NUMBER, "REC-2026-00412");
    }

    // --- values with a label in front ----------------------------------------

    @Test
    void aLabelledDocumentNumberIsRead() {
        assertThat(from("mi dni es 20123456")).containsEntry(DNI, "20123456");
    }

    @Test
    void theDotsPeopleWriteDocumentNumbersWithAreNotPartOfIt() {
        assertThat(from("dni 20.123.456")).containsEntry(DNI, "20123456");
    }

    @Test
    void spacesBetweenTheGroupsAreNotEither() {
        assertThat(from("documento 20 123 456")).containsEntry(DNI, "20123456");
    }

    @Test
    void aLabelReachesForwardOnlySoFar() {
        // "dni" three words back still labels the number. Any further and it is a
        // sentence that happens to mention both.
        assertThat(from("el dni de mi mama es 20123456")).isEmpty();
    }

    @Test
    void aLabelledNumberOfTheWrongShapeIsNotRead() {
        // Seven or eight digits is what a document number is. Six is a typo, and acting
        // on a typo is worse than asking again.
        assertThat(from("mi dni es 201234")).isEmpty();
    }

    @Test
    void aLabelledClaimNumberIsRead() {
        assertThat(from("el reclamo 4471 sigue igual")).containsEntry(CLAIM_NUMBER, "4471");
    }

    // --- bare values ---------------------------------------------------------

    @Test
    void aBareNumberIsReadAsTheOneThingThatWasAskedFor() {
        assertThat(from("4471", CLAIM_NUMBER)).containsEntry(CLAIM_NUMBER, "4471");
    }

    @Test
    void aBareNumberIsNothingWhenNothingWasAsked() {
        assertThat(from("4471")).isEmpty();
    }

    @Test
    void aBareNumberIsNothingWhenTwoThingsWereAsked() {
        // With two questions open there is no reading of "20123456" that is not a guess.
        assertThat(extractor.extract("20123456", Set.of(DNI, CLAIM_NUMBER))).isEmpty();
    }

    @Test
    void aBareNumberThatDoesNotFitWhatWasAskedIsNotRead() {
        assertThat(from("12", CLAIM_NUMBER)).isEmpty();
    }

    @Test
    void aHouseNumberInAComplaintIsNotAnIdentifier() {
        assertThat(from("se rompio una luminaria en Sarmiento 450")).isEmpty();
    }

    @Test
    void aLabelBeatsWhatWasExpected() {
        // They were asked for a claim number and answered with their document number,
        // and said so. Taking them at their word is the only honest reading.
        assertThat(from("mi dni es 20123456", CLAIM_NUMBER)).containsEntry(DNI, "20123456");
    }

    // --- more than one at a time ---------------------------------------------

    @Test
    void twoLabelledValuesAreBothRead() {
        var found = from("dni 20123456 y el reclamo REC-2026-00412");

        assertThat(found).containsEntry(DNI, "20123456").containsEntry(CLAIM_NUMBER, "REC-2026-00412");
    }

    @Test
    void theFirstReadingOfAValueIsTheOneThatStands() {
        assertThat(from("dni 20123456 dni 30999888")).containsEntry(DNI, "20123456");
    }

    @Test
    void nothingIsReadOutOfAMessageWithNoNumbersInIt() {
        assertThat(from("hola, buenas tardes")).isEmpty();
    }

    @Test
    void whatComesBackCannotBeChanged() {
        var found = from("dni 20123456");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> found.put(CLAIM_NUMBER, "1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
