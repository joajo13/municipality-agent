package com.municipality.agent.conversation;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.Intent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.municipality.agent.router.EntityType.CLAIM_NUMBER;
import static com.municipality.agent.router.EntityType.DNI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the agent remembers about one resident, and the two questions it answers: is a
 * bare message an answer to what was asked, and is this still the same conversation at
 * all.
 */
class ConversationTest {

    private static final Instant NOON = Instant.parse("2026-08-24T12:00:00Z");
    private static final Duration HALF_AN_HOUR = Duration.ofMinutes(30);

    private static final Intent CHECKING_A_CLAIM = new Intent(Domain.RECLAMOS, Action.CHECK_STATUS, 1.0);
    private static final Intent UNPLACEABLE = new Intent(Domain.UNKNOWN, Action.INFORMATION, 0.0);

    private static Conversation waitingForAClaimNumber() {
        return Conversation.startedBy("user-1", NOON)
                .after(new OpenQuestion(CHECKING_A_CLAIM, Set.of(CLAIM_NUMBER)), NOON);
    }

    // --- starting ------------------------------------------------------------

    @Test
    void aNewConversationKnowsNothingAndIsWaitingForNothing() {
        var conversation = Conversation.startedBy("user-1", NOON);

        assertThat(conversation.known()).isEmpty();
        assertThat(conversation.asked()).isNull();
        assertThat(conversation.turns()).isZero();
        assertThat(conversation.expecting()).isEmpty();
    }

    @Test
    void aConversationWithoutSomebodyOnTheOtherEndIsNotAConversation() {
        assertThatThrownBy(() -> Conversation.startedBy("  ", NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void turnsCannotRunBackwards() {
        assertThatThrownBy(() -> new Conversation("user-1", Map.of(), null, -1, NOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTurnHasToHaveHappenedAtSomePoint() {
        assertThatThrownBy(() -> new Conversation("user-1", Map.of(), null, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nothingKnownIsNotTheSameAsNoMemoryAtAll() {
        assertThatThrownBy(() -> new Conversation("user-1", null, null, 0, NOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whatIsKnownCannotBeChangedFromOutside() {
        var known = new java.util.HashMap<>(Map.of(DNI, "20123456"));
        var conversation = new Conversation("user-1", known, null, 1, NOON);

        known.clear();

        assertThat(conversation.known()).containsEntry(DNI, "20123456");
    }

    // --- still open? ---------------------------------------------------------

    @Test
    void aConversationIsStillOpenWhileTheNextMessageIsSoonEnough() {
        var conversation = Conversation.startedBy("user-1", NOON);

        assertThat(conversation.isOpenAt(NOON.plus(Duration.ofMinutes(29)), HALF_AN_HOUR)).isTrue();
    }

    @Test
    void aConversationIsStillOpenOnTheLastSecondOfIt() {
        var conversation = Conversation.startedBy("user-1", NOON);

        assertThat(conversation.isOpenAt(NOON.plus(HALF_AN_HOUR), HALF_AN_HOUR)).isTrue();
    }

    @Test
    void aConversationNobodyCameBackToIsOver() {
        var conversation = Conversation.startedBy("user-1", NOON);

        assertThat(conversation.isOpenAt(NOON.plus(Duration.ofHours(8)), HALF_AN_HOUR)).isFalse();
    }

    @Test
    void forgettingLeavesTheResidentAndTheCount() {
        var conversation = waitingForAClaimNumber().learned(Map.of(DNI, "20123456"));

        var forgotten = conversation.forgotten(NOON.plus(Duration.ofHours(8)));

        assertThat(forgotten.known()).isEmpty();
        assertThat(forgotten.asked()).isNull();
        assertThat(forgotten.userId()).isEqualTo("user-1");
        assertThat(forgotten.turns()).isEqualTo(conversation.turns());
    }

    // --- reading an answer ---------------------------------------------------

    @Test
    void aMessageNobodyCouldPlaceIsAnAnswerToWhatWasAsked() {
        assertThat(waitingForAClaimNumber().read(UNPLACEABLE)).isEqualTo(CHECKING_A_CLAIM);
    }

    @Test
    void aMessageAboutSomethingElseIsNotAnAnswer() {
        var changedSubject = new Intent(Domain.LICENCIAS, Action.START_PROCEDURE, 1.0);

        assertThat(waitingForAClaimNumber().read(changedSubject)).isEqualTo(changedSubject);
    }

    @Test
    void askingForAPersonIsNeverAnAnswer() {
        var wantsAPerson = new Intent(Domain.UNKNOWN, Action.HANDOFF, 1.0);

        assertThat(waitingForAClaimNumber().read(wantsAPerson)).isEqualTo(wantsAPerson);
    }

    @Test
    void withNothingAskedEveryMessageStandsOnItsOwn() {
        assertThat(Conversation.startedBy("user-1", NOON).read(UNPLACEABLE)).isEqualTo(UNPLACEABLE);
    }

    @Test
    void whatIsExpectedIsWhatWasAskedFor() {
        assertThat(waitingForAClaimNumber().expecting()).containsExactly(CLAIM_NUMBER);
    }

    // --- learning ------------------------------------------------------------

    @Test
    void whatTheResidentGivesIsKept() {
        var conversation = Conversation.startedBy("user-1", NOON).learned(Map.of(DNI, "20123456"));

        assertThat(conversation.known()).containsEntry(DNI, "20123456");
    }

    @Test
    void beingToldNothingChangesNothing() {
        var conversation = Conversation.startedBy("user-1", NOON);

        assertThat(conversation.learned(Map.of())).isSameAs(conversation);
    }

    @Test
    void whatTheySayNowIsWhatCounts() {
        // A corrected document number is a correction, not a second opinion.
        var conversation = Conversation.startedBy("user-1", NOON)
                .learned(Map.of(DNI, "20123456"))
                .learned(Map.of(DNI, "30999888"));

        assertThat(conversation.known()).containsEntry(DNI, "30999888");
    }

    @Test
    void learningDoesNotCountAsATurn() {
        // A turn is written once, at the end of it. Reading a number out of a message is
        // not the end of anything.
        assertThat(Conversation.startedBy("user-1", NOON).learned(Map.of(DNI, "20123456")).turns()).isZero();
    }

    // --- closing a turn ------------------------------------------------------

    @Test
    void aTurnMovesTheCountAndTheClock() {
        var later = NOON.plus(Duration.ofMinutes(5));

        var conversation = Conversation.startedBy("user-1", NOON).after(null, later);

        assertThat(conversation.turns()).isEqualTo(1);
        assertThat(conversation.lastSeen()).isEqualTo(later);
    }

    @Test
    void aQuestionHasToBeAboutSomething() {
        assertThatThrownBy(() -> new OpenQuestion(null, Set.of(DNI))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aQuestionThatAsksForNothingIsNotAQuestion() {
        assertThatThrownBy(() -> new OpenQuestion(CHECKING_A_CLAIM, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenQuestion(CHECKING_A_CLAIM, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
