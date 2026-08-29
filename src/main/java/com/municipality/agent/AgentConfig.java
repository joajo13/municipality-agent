package com.municipality.agent;

import com.municipality.agent.conversation.Conversations;
import com.municipality.agent.conversation.InMemoryConversations;
import com.municipality.agent.extraction.EntityExtractor;
import com.municipality.agent.extraction.PatternEntityExtractor;
import com.municipality.agent.message.MediaDescriber;
import com.municipality.agent.message.NoMediaDescriber;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.router.KeywordClassifier;
import com.municipality.agent.router.ModelClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the agent out of its parts.
 *
 * <p>None of those parts knows that Spring exists — there is not one annotation across
 * the message, router and policy packages. The wiring lives here instead, which keeps
 * the domain testable as plain Java and makes replacing a part a one-line change in this
 * file rather than a hunt through annotations.
 *
 * <p>The two returned as interfaces are the two that get replaced: a real transcription
 * and vision model in place of {@link NoMediaDescriber}, which has not arrived yet, and
 * {@link ModelClassifier} in place of {@link KeywordClassifier}, which has — and which
 * one runs is settled here, at startup, by configuration. Everything downstream is
 * injected with the interface and does not notice either way.
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Bean
    MediaDescriber mediaDescriber() {
        return new NoMediaDescriber();
    }

    @Bean
    Normalizer normalizer(MediaDescriber mediaDescriber) {
        return new Normalizer(mediaDescriber);
    }

    /**
     * The real classifier when a chat model is configured, and the keyword stand-in when
     * there is none.
     *
     * <p>Which one runs is decided by whether a {@link ChatModel} bean exists, and that
     * is decided by configuration alone — see {@code application.yaml}, where the
     * default is no model at all. So the console still runs with no API key and no
     * network, and the suite never depends on either.
     *
     * <p>It is logged because the two behave nothing alike, and a stand-in that quietly
     * took over would look exactly like a model having a bad day.
     */
    @Bean
    Classifier classifier(ObjectProvider<ChatModel> models) {
        ChatModel model = models.getIfAvailable();

        if (model == null) {
            log.info("No chat model configured -- classifying by keyword. Run with the 'ai' profile for the model.");
            return new KeywordClassifier();
        }

        log.info("Classifying with {}.", model.getClass().getSimpleName());
        return new ModelClassifier(ChatClient.create(model));
    }

    @Bean
    EntityExtractor entityExtractor() {
        return new PatternEntityExtractor();
    }

    @Bean
    Policy policy() {
        return new Policy();
    }

    /**
     * Conversations in memory, unless something else was put on the classpath and
     * configured — the store is the part that has to change first for a second instance
     * of this service to be worth running.
     */
    @Bean
    @ConditionalOnMissingBean(Conversations.class)
    Conversations conversations() {
        log.info("Conversations are kept in memory -- one instance only, and forgotten on restart.");
        return new InMemoryConversations();
    }

    @Bean
    Agent agent(
            Normalizer normalizer,
            EntityExtractor extractor,
            Classifier classifier,
            Policy policy,
            Conversations conversations,
            AgentProperties properties) {

        return new Agent(normalizer, extractor, classifier, policy, conversations, properties.idleTimeout());
    }
}
