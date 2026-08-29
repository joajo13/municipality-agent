package com.municipality.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A body larger than any message could be.
 *
 * <p>Without a limit the size of a request is decided by whoever is sending it: the body
 * arrives, the parser reads all of it into memory, and the process is gone before a single
 * field has been looked at.
 */
@SpringBootTest(properties = {"agent.api.key=" + RequestSizeTest.KEY, "agent.api.max-request-bytes=512"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestSizeTest {

    static final String KEY = "a-test-key";

    @Autowired
    private MockMvc mvc;

    private static String messageOf(int length) {
        return """
                {"from": "+5493415551234", "contents": [{"type": "text", "body": "%s"}]}
                """.formatted("a".repeat(length));
    }

    @Test
    void aMessageOfAReasonableSizeGoesThrough() throws Exception {
        mvc.perform(post("/api/v1/messages")
                        .header(ApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageOf(100)))
                .andExpect(status().isOk());
    }

    @Test
    void aBodyBiggerThanAnyMessageIsRefusedBeforeItIsRead() throws Exception {
        mvc.perform(post("/api/v1/messages")
                        .header(ApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageOf(2000)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void aLimitOfNothingIsNotALimit() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestSize(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
