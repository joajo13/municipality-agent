package com.municipality.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The endpoint, end to end: a real agent, a real schema, a real filter chain.
 *
 * <p>The subject here is everything the adapter adds and the agent does not — the key on
 * the door, the shape of a bad request, the second delivery of the same message. What the
 * agent decides is somebody else's test, which is why the assertions below are about
 * envelopes and status codes rather than about complaints and licences.
 */
@SpringBootTest(properties = {"agent.api.key=" + MessageControllerTest.KEY, "agent.api.messages-per-window=1000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageControllerTest {

    static final String KEY = "a-test-key";

    private static final String ENDPOINT = "/api/v1/messages";

    @Autowired
    private MockMvc mvc;

    private static String message(String from, String body) {
        return message(UUID.randomUUID().toString(), from, body);
    }

    private static String message(String id, String from, String body) {
        return """
                {"messageId": "%s", "from": "%s", "sentAt": "2026-08-24T10:00:00Z",
                 "contents": [{"type": "text", "body": "%s"}]}
                """.formatted(id, from, body);
    }

    private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(post(ENDPOINT)
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // --- the door ------------------------------------------------------------

    @Test
    void withoutTheKeyThereIsNothingHere() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(message("+5491111", "hola")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString(ApiKeyFilter.HEADER)));
    }

    @Test
    void theWrongKeyIsTheSameAsNoKey() throws Exception {
        mvc.perform(post(ENDPOINT)
                        .header(ApiKeyFilter.HEADER, "not-the-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(message("+5491111", "hola")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nothingElseIsServedAtAll() throws Exception {
        mvc.perform(post("/something-else").header(ApiKeyFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
    }

    // --- a message -----------------------------------------------------------

    @Test
    void aMessageComesBackAnswered() throws Exception {
        send(message("+5492221", "quiero consultar el estado de mi reclamo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("número de reclamo")))
                .andExpect(jsonPath("$.decision").value("AskFor [CLAIM_NUMBER]"))
                .andExpect(jsonPath("$.intent.domain").value("RECLAMOS"))
                .andExpect(jsonPath("$.intent.action").value("CHECK_STATUS"))
                .andExpect(jsonPath("$.conversation.turn").value(1))
                .andExpect(jsonPath("$.conversation.awaiting[0]").value("CLAIM_NUMBER"));
    }

    @Test
    void aTurnAnsweredWithoutAModelReportsNoUsage() throws Exception {
        send(message("+5492222", "hola")).andExpect(jsonPath("$.usage").doesNotExist());
    }

    @Test
    void aConversationCarriesOnAcrossRequests() throws Exception {
        send(message("+5492223", "quiero consultar el estado de mi reclamo")).andExpect(status().isOk());

        send(message("+5492223", "4471"))
                .andExpect(jsonPath("$.decision").value("StartFlow RECLAMOS / CHECK_STATUS"))
                .andExpect(jsonPath("$.conversation.turn").value(2))
                .andExpect(jsonPath("$.conversation.known[0]").value("CLAIM_NUMBER"));
    }

    @Test
    void whatTheResidentGaveIsNeverInTheAnswer() throws Exception {
        // The names of what is known, never the values. This response is going into
        // somebody else's logs as well as ours.
        send(message("+5492224", "mi dni es 20123456 y quiero sacar la licencia"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("20123456"))));
    }

    // --- the same message twice ----------------------------------------------

    @Test
    void aRedeliveryIsAnsweredWithTheAnswerFromLastTime() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = message(id, "+5492225", "quiero consultar el estado de mi reclamo");

        send(body).andExpect(status().isOk())
                .andExpect(header().string(MessageController.REPLAY, "false"))
                .andExpect(jsonPath("$.conversation.turn").value(1));

        // The turn does not happen again: the conversation is still on turn 1.
        send(body).andExpect(status().isOk())
                .andExpect(header().string(MessageController.REPLAY, "true"))
                .andExpect(jsonPath("$.conversation.turn").value(1));
    }

    @Test
    void aMessageWithNoIdOfItsOwnIsAlwaysANewMessage() throws Exception {
        String body = """
                {"from": "+5492226", "contents": [{"type": "text", "body": "hola"}]}
                """;

        send(body).andExpect(jsonPath("$.conversation.turn").value(1));
        send(body).andExpect(jsonPath("$.conversation.turn").value(2));
    }

    // --- bad requests --------------------------------------------------------

    @Test
    void aBodyThatIsNotJsonIsRefusedWithoutQuotingIt() throws Exception {
        send("this is not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unreadable request"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("this is not json"))));
    }

    @Test
    void aMessageFromNobodyIsRefused() throws Exception {
        send("""
                {"from": "", "contents": [{"type": "text", "body": "hola"}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", hasItem(containsString("from"))));
    }

    @Test
    void aMessageWithNothingInItIsRefused() throws Exception {
        send("""
                {"from": "+5492227", "contents": []}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", hasItem(containsString("contents"))));
    }

    @Test
    void aContentTypeNobodyDefinedIsRefused() throws Exception {
        send("""
                {"from": "+5492228", "contents": [{"type": "hologram", "body": "hola"}]}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void whatWasWrongIsSaidWithoutRepeatingWhatWasSent() throws Exception {
        send("""
                {"from": "+5492229", "contents": [{"type": "text", "body": ""}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("One or more fields are not acceptable."));
    }
}
