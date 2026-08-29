package com.municipality.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What happens when one resident sends too much.
 *
 * <p>Its own context because the limit has to be set to something a test can reach, and a
 * limit of one is the only value that makes the second request the interesting one.
 */
@SpringBootTest(properties = {
        "agent.api.key=" + MessageRateLimitTest.KEY,
        "agent.api.messages-per-window=1",
        "agent.api.window=1m"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageRateLimitTest {

    static final String KEY = "a-test-key";

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions send(String from) throws Exception {
        String body = """
                {"messageId": "%s", "from": "%s", "contents": [{"type": "text", "body": "hola"}]}
                """.formatted(UUID.randomUUID(), from);

        return mvc.perform(post("/api/v1/messages")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void theSecondMessageInTheWindowIsRefusedWithSomethingToDoAboutIt() throws Exception {
        send("+5493410001").andExpect(status().isOk());

        send("+5493410001")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(MessageController.RETRY_AFTER))
                .andExpect(jsonPath("$.title").value("Too many messages"));
    }

    @Test
    void oneResidentDoesNotSpendAnotherOnesAllowance() throws Exception {
        send("+5493410002").andExpect(status().isOk());

        send("+5493410003").andExpect(status().isOk());
    }
}
