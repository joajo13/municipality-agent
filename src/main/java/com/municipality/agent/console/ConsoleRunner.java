package com.municipality.agent.console;

import com.municipality.agent.Outcome;
import com.municipality.agent.Turns;
import com.municipality.agent.message.IncomingMessage;
import com.municipality.agent.message.Text;
import org.springframework.boot.CommandLineRunner;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.Clock;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Read-eval-print loop. Reads what you type, hands it to the agent, prints what the
 * agent decided and how it got there.
 *
 * <p>Input and output arrive through the constructor rather than being taken from
 * {@code System.in} and {@code System.out} inside the loop. {@link ConsoleConfig} hands it
 * the real terminal; a test hands it a string to read from and a buffer to write into.
 *
 * <p>It only exists under the {@code console} profile. The service's own way of running is
 * to sit there and be sent messages; a process that reads standard input is a developer
 * tool, and one that reads standard input in production is a process waiting on a
 * terminal nobody is at.
 *
 * <p>Ending the loop has to end the process, and that is what {@code whenDone} is for.
 * There are non-daemon threads in a started service — a scheduler, a connection pool —
 * and typing "exit" into a REPL that then hangs is a poor demonstration of anything.
 */
public class ConsoleRunner implements CommandLineRunner {

    private static final String EXIT_COMMAND = "exit";

    /** Every message typed here belongs to the same imaginary resident. */
    private static final String CONSOLE_USER = "console";

    private final BufferedReader input;
    private final PrintWriter output;
    private final Turns turns;
    private final DecisionRenderer renderer = new DecisionRenderer();

    private final Clock clock;
    private final Runnable whenDone;

    public ConsoleRunner(Reader input, PrintWriter output, Turns turns, Clock clock) {
        this(input, output, turns, clock, () -> {});
    }

    public ConsoleRunner(Reader input, PrintWriter output, Turns turns, Clock clock, Runnable whenDone) {
        this.input = new BufferedReader(input);
        this.output = output;
        this.turns = turns;
        this.clock = clock;
        this.whenDone = whenDone;
    }

    @Override
    public void run(String... args) throws Exception {
        output.println();
        output.println("Municipality agent. Type '" + EXIT_COMMAND + "' to quit.");
        output.println();

        while (true) {
            output.print("you > ");
            output.flush(); // print() never flushes on its own, and the prompt has no newline

            String line = input.readLine();

            if (line == null || line.trim().equalsIgnoreCase(EXIT_COMMAND)) {
                break;
            }
            if (line.isBlank()) {
                continue;
            }

            print(turns.handle(asMessage(line)));
        }

        output.println("Bye.");
        output.flush();

        whenDone.run();
    }

    /** What this turn cost, and what it spent it on. */
    private static String priceOf(Outcome outcome) {
        var trace = outcome.trace();
        var call = trace.call();

        if (call == null) return "-";

        return trace.cost().currency() + " " + trace.cost().amount().toPlainString()
                + "  (" + call.inputTokens() + " in / " + call.outputTokens() + " out, " + call.model() + ")";
    }

    /** Wraps a typed line as if it had arrived from a messaging provider. */
    private IncomingMessage asMessage(String line) {
        return new IncomingMessage(
                UUID.randomUUID().toString(), CONSOLE_USER, clock.instant(), List.of(new Text(line)));
    }

    /**
     * The trace above the reply, and never a value out of it. What a resident handed over
     * is their document number: the names of what is known say everything a developer
     * reading this needs, and none of it is anybody's identity.
     */
    private void print(Outcome outcome) {
        var intent = outcome.intent();
        var conversation = outcome.conversation();

        output.println();
        output.println("  turno      " + conversation.turns());
        output.println("  texto      " + outcome.message().text());
        output.println("  intent     " + intent.domain() + " / " + intent.action() + "  (" + intent.confidence() + ")");
        output.println("  decision   " + renderer.summary(outcome.decision()));

        if (!outcome.given().isEmpty()) {
            output.println("  recibido   " + new TreeSet<>(outcome.given().keySet()));
        }
        if (!conversation.known().isEmpty()) {
            output.println("  recordado  " + new TreeSet<>(conversation.known().keySet()));
        }
        if (outcome.trace().reachedAModel()) {
            output.println("  costo      " + priceOf(outcome));
        }

        output.println();
        output.println("bot > " + renderer.reply(outcome.decision()));
        output.println();
    }
}
