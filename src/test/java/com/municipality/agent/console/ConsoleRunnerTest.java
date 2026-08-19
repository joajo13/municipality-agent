package com.municipality.agent.console;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No Spring context here on purpose: the runner takes its input and output as
 * constructor arguments, so the loop can be exercised as plain Java.
 */
class ConsoleRunnerTest {

    /** Feeds {@code typed} to the console as if a user had typed it; returns everything printed. */
    private String run(String typed) throws Exception {
        var output = new StringWriter();
        new ConsoleRunner(new StringReader(typed), new PrintWriter(output)).run();
        // println() emits \r\n on Windows and \n elsewhere. Normalise so the
        // assertions below read the same on every machine.
        return output.toString().replace("\r\n", "\n");
    }

    @Test
    void echoesWhatYouType() throws Exception {
        assertThat(run("hola\nexit\n")).contains("bot > echo: hola\n");
    }

    @Test
    void greetsBeforeReadingAnything() throws Exception {
        assertThat(run("exit\n")).contains("Municipality agent. Type 'exit' to quit.");
    }

    @Test
    void exitStopsTheLoopWithoutEchoing() throws Exception {
        assertThat(run("exit\n"))
                .doesNotContain("echo:")
                .contains("Bye.");
    }

    @Test
    void exitIgnoresCaseAndSurroundingSpaces() throws Exception {
        assertThat(run("  EXIT  \n"))
                .doesNotContain("echo:")
                .contains("Bye.");
    }

    @Test
    void blankLinesAreSkipped() throws Exception {
        assertThat(run("\n   \nhola\nexit\n")).containsOnlyOnce("bot > echo:");
    }

    @Test
    void echoedLineIsTrimmed() throws Exception {
        assertThat(run("   hola   \nexit\n")).contains("bot > echo: hola\n");
    }

    @Test
    void endOfInputAlsoStopsTheLoop() throws Exception {
        // Nobody typed "exit" -- the stream just ran out and readLine() returned null.
        assertThat(run("hola\n"))
                .contains("bot > echo: hola")
                .contains("Bye.");
    }

    @Test
    void promptsOncePerLineItTriesToRead() throws Exception {
        // Three prompts: before "uno", before "dos", before "exit".
        assertThat(run("uno\ndos\nexit\n").split("you > ", -1)).hasSize(4);
    }
}
