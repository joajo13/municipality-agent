package com.municipality.agent.console;

import com.municipality.agent.observability.Turns;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

/**
 * Wires the real terminal into {@link ConsoleRunner}. Tests never load this
 * class: they construct the runner themselves with an in-memory reader/writer.
 *
 * <p>{@code destroyMethod = ""} stops Spring from calling {@code close()} on
 * these beans at shutdown, which would close {@code System.in} / {@code System.out}
 * for the whole JVM.
 */
@Configuration
@Profile("console")
public class ConsoleConfig {

    @Bean(destroyMethod = "")
    Reader consoleInput() {
        return new InputStreamReader(System.in);
    }

    @Bean(destroyMethod = "")
    PrintWriter consoleOutput() {
        return new PrintWriter(System.out);
    }

    /**
     * The REPL is the whole process here, so the end of the loop is the end of the run.
     * A started service has non-daemon threads in it — a scheduler, a connection pool —
     * and without this, typing "exit" would print "Bye." and then sit there.
     */
    @Bean
    ConsoleRunner consoleRunner(Reader consoleInput, PrintWriter consoleOutput, Turns turns, ApplicationContext context) {
        return new ConsoleRunner(consoleInput, consoleOutput, turns,
                () -> System.exit(SpringApplication.exit(context)));
    }
}
