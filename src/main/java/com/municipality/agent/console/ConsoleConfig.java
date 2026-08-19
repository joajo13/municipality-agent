package com.municipality.agent.console;

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
@Profile("!test")
public class ConsoleConfig {

    @Bean(destroyMethod = "")
    Reader consoleInput() {
        return new InputStreamReader(System.in);
    }

    @Bean(destroyMethod = "")
    PrintWriter consoleOutput() {
        return new PrintWriter(System.out);
    }
}
