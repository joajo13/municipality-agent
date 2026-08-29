package com.municipality.agent;

import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.delivery.InMemoryReceipts;
import com.municipality.agent.delivery.Receipts;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.ai.ModelClassifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the parts are put together, under the two configurations that change which parts
 * they are.
 *
 * <p>Both of these are one-line decisions in {@code AgentConfig}, and both of them decide
 * something large: whether a second instance of this service is worth running, and whether
 * every message costs money. A one-line decision that nothing checks is a one-line
 * decision that silently flips.
 */
class AgentAssemblyTest {

    @Nested
    @SpringBootTest(properties = "agent.store=memory")
    @ActiveProfiles("test")
    class WithNothingBehindIt {

        @Autowired
        private Conversations conversations;

        @Autowired
        private Receipts receipts;

        @Test
        void everythingIsKeptInMemory() {
            assertThat(conversations).isInstanceOf(InMemoryConversations.class);
            assertThat(receipts).isInstanceOf(InMemoryReceipts.class);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class WithAModelConfigured {

        @TestConfiguration
        static class AModelThatAnswersLocally {

            @Bean
            ChatModel chatModel() {
                return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"domain\": \"SALUD\", \"action\": \"INFORMATION\", \"confidence\": 0.9}"))));
            }
        }

        @Autowired
        private Classifier classifier;

        @Test
        void theModelClassifierIsWhatRuns() {
            assertThat(classifier).isInstanceOf(ModelClassifier.class);
        }
    }
}
