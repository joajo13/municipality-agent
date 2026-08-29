package com.municipality.agent.console;

import com.municipality.agent.Turns;
import com.municipality.agent.support.Agents;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No Spring context here on purpose: the runner takes its input, its output and its
 * agent as constructor arguments, so the loop can be exercised as plain Java.
 *
 * <p>The agent is the real one. Nothing in the pipeline reaches the network yet, so
 * standing anything in would only test the stand-in.
 */
class ConsoleRunnerTest {

    private static Turns realAgent() {
        return Agents.watched(Agents.keyword());
    }

    /** The same agent, billed as if a model had answered, so the cost line appears. */
    private static Turns billedAgent() {
        return Agents.watched(Agents.around(
                Agents.spending(412, 18), new com.municipality.agent.conversation.InMemoryConversations()));
    }

    /** Feeds {@code typed} to the console as if a user had typed it; returns everything printed. */
    private String run(String typed) throws Exception {
        var output = new StringWriter();
        new ConsoleRunner(new StringReader(typed), new PrintWriter(output), realAgent(), NOON).run();
        // println() emits \r\n on Windows and \n elsewhere. Normalise so the
        // assertions below read the same on every machine.
        return output.toString().replace("\r\n", "\n");
    }

    /** A clock that does not move, so two runs of the same script are the same run. */
    private static final java.time.Clock NOON =
            java.time.Clock.fixed(java.time.Instant.parse("2026-08-24T12:00:00Z"), java.time.ZoneOffset.UTC);

    private static String runWith(Turns turns, String typed) throws Exception {
        var output = new StringWriter();
        new ConsoleRunner(new StringReader(typed), new PrintWriter(output), turns, NOON).run();

        return output.toString().replace("\r\n", "\n");
    }

    // --- the trace under the answer ------------------------------------------

    @Test
    void theTraceSaysWhatWasHandedOverAndWhatIsRemembered() throws Exception {
        var printed = run("quiero sacar la licencia\nmi dni es 20123456\nexit\n");

        assertThat(printed).contains("recibido   [DNI]").contains("recordado  [DNI]");
    }

    @Test
    void theTraceNeverPrintsTheValueOfAnything() throws Exception {
        // A developer reading this needs to know a document number arrived, not what it
        // was. The console is a window onto somebody's conversation.
        assertThat(run("mi dni es 20123456\nexit\n")).doesNotContain("20123456 ");
    }

    @Test
    void aTurnThatCostSomethingSaysWhatItCost() throws Exception {
        var printed = runWith(billedAgent(), "quiero sacar la licencia\nexit\n");

        assertThat(printed).contains("costo      USD 0.000502").contains("412 in / 18 out");
    }

    @Test
    void aTurnThatCostNothingDoesNotMentionMoney() throws Exception {
        assertThat(run("hola\nexit\n")).doesNotContain("costo");
    }

    @Test
    void endingTheLoopEndsWhateverIsHoldingTheProcessOpen() throws Exception {
        var stopped = new java.util.concurrent.atomic.AtomicBoolean();
        var output = new StringWriter();

        new ConsoleRunner(new StringReader("exit\n"), new PrintWriter(output), realAgent(), NOON,
                () -> stopped.set(true)).run();

        assertThat(stopped).isTrue();
    }

    // --- the loop itself -----------------------------------------------------

    @Test
    void greetsBeforeReadingAnything() throws Exception {
        assertThat(run("exit\n")).contains("Municipality agent. Type 'exit' to quit.");
    }

    @Test
    void exitStopsTheLoopWithoutClassifyingAnything() throws Exception {
        assertThat(run("exit\n")).doesNotContain("intent").contains("Bye.");
    }

    @Test
    void exitIgnoresCaseAndSurroundingSpaces() throws Exception {
        assertThat(run("  EXIT  \n")).doesNotContain("intent").contains("Bye.");
    }

    @Test
    void blankLinesAreSkipped() throws Exception {
        assertThat(run("\n   \nexit\n")).doesNotContain("intent");
    }

    @Test
    void endOfInputAlsoStopsTheLoop() throws Exception {
        // Nobody typed "exit" -- the stream just ran out and readLine() returned null.
        assertThat(run("hola\n")).contains("Bye.");
    }

    @Test
    void promptsOncePerLineItTriesToRead() throws Exception {
        // Three prompts: before "uno", before "dos", before "exit".
        assertThat(run("uno\ndos\nexit\n").split("you > ", -1)).hasSize(4);
    }

    // --- what it shows -------------------------------------------------------

    @Test
    void showsTheTextThatWasClassified() throws Exception {
        assertThat(run("quiero sacar la licencia\nexit\n")).contains("quiero sacar la licencia");
    }

    @Test
    void showsWhatItUnderstood() throws Exception {
        var printed = run("quiero sacar la licencia\nexit\n");

        assertThat(printed).contains("LICENCIAS").contains("START_PROCEDURE");
    }

    @Test
    void showsWhichDecisionItReached() throws Exception {
        // Licences need a dni and nothing is known yet, so it has to ask.
        assertThat(run("quiero sacar la licencia\nexit\n")).contains("AskFor");
    }

    @Test
    void repliesToTheResidentInSpanish() throws Exception {
        assertThat(run("quiero sacar la licencia\nexit\n")).contains("bot > ").contains("DNI");
    }

    @Test
    void noLongerEchoes() throws Exception {
        // It used to repeat the line back. That was the placeholder for all of this.
        assertThat(run("hola\nexit\n")).doesNotContain("echo:");
    }
}
