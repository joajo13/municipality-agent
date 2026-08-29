package com.municipality.agent.console;

import com.municipality.agent.observability.Turns;
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

    /** Feeds {@code typed} to the console as if a user had typed it; returns everything printed. */
    private String run(String typed) throws Exception {
        var output = new StringWriter();
        new ConsoleRunner(new StringReader(typed), new PrintWriter(output), realAgent()).run();
        // println() emits \r\n on Windows and \n elsewhere. Normalise so the
        // assertions below read the same on every machine.
        return output.toString().replace("\r\n", "\n");
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
