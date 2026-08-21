package com.municipality.agent;

import com.municipality.agent.message.MediaDescriber;
import com.municipality.agent.message.NoMediaDescriber;
import com.municipality.agent.message.Normalizer;
import com.municipality.agent.policy.Policy;
import com.municipality.agent.router.Classifier;
import com.municipality.agent.router.KeywordClassifier;
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
 * and vision model in place of {@link NoMediaDescriber}, and a real model in place of
 * {@link KeywordClassifier}. Everything downstream is injected with the interface and
 * will not notice.
 */
@Configuration
public class AgentConfig {

    @Bean
    MediaDescriber mediaDescriber() {
        return new NoMediaDescriber();
    }

    @Bean
    Normalizer normalizer(MediaDescriber mediaDescriber) {
        return new Normalizer(mediaDescriber);
    }

    @Bean
    Classifier classifier() {
        return new KeywordClassifier();
    }

    @Bean
    Policy policy() {
        return new Policy();
    }

    @Bean
    Agent agent(Normalizer normalizer, Classifier classifier, Policy policy) {
        return new Agent(normalizer, classifier, policy);
    }
}
