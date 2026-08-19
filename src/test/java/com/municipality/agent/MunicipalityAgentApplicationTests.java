package com.municipality.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MunicipalityAgentApplicationTests {

    /**
     * Verifies the Spring context starts. The "test" profile keeps {@code ConsoleRunner}
     * out of it: @SpringBootTest *does* execute CommandLineRunner beans, so without this
     * the console would launch and block forever waiting on System.in.
     */

    @Test
    void contextLoads() {
    }

}
