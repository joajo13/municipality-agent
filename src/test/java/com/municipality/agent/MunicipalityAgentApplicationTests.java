package com.municipality.agent;

import com.municipality.agent.router.Classifier;
import com.municipality.agent.router.KeywordClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MunicipalityAgentApplicationTests {

    @Autowired
    private Classifier classifier;

    /**
     * Verifies the Spring context starts. The "test" profile keeps {@code ConsoleRunner}
     * out of it: @SpringBootTest *does* execute CommandLineRunner beans, so without this
     * the console would launch and block forever waiting on System.in.
     */

    @Test
    void contextLoads() {
    }

    /**
     * With no chat model configured — which is the default, and what every run of this
     * suite gets — the agent is assembled around the keyword stand-in. Nothing here
     * needs an API key, and nothing here reaches the network.
     */
    @Test
    void withoutAModelTheAgentFallsBackToKeywords() {
        assertThat(classifier).isInstanceOf(KeywordClassifier.class);
    }

}
