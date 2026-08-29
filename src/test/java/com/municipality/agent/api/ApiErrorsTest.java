package com.municipality.agent.api;

import com.municipality.agent.conversation.ConcurrentTurn;
import com.municipality.agent.conversation.Conversation;
import com.municipality.agent.conversation.Conversations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the caller is told when a turn loses a race.
 *
 * <p>409 and not 500, because nothing is broken and there is something useful to do about
 * it. The store here always refuses the write, which is what a second instance answering
 * the same resident looks like from in here.
 */
@SpringBootTest(properties = "agent.api.key=" + ApiErrorsTest.KEY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorsTest {

    static final String KEY = "a-test-key";

    @Autowired
    private MockMvc mvc;

    @TestConfiguration
    static class SomebodyElseIsAlwaysFaster {

        @Bean
        @Primary
        Conversations conversations() {
            return new Conversations() {

                @Override
                public Optional<Conversation> of(String userId) {
                    return Optional.empty();
                }

                @Override
                public Conversation save(Conversation conversation) {
                    throw new ConcurrentTurn(conversation.userId());
                }
            };
        }
    }

    @Test
    void aTurnThatLostARaceComesBackAsSomethingToRetry() throws Exception {
        mvc.perform(post("/api/v1/messages")
                        .header(ApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from": "+5493415551234", "contents": [{"type": "text", "body": "hola"}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conversation moved on"))
                .andExpect(jsonPath("$.type").value("https://municipality-agent/problems/conversation-moved-on"));
    }
}
