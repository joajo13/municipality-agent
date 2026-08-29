package com.municipality.agent.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * What an operator sets about the endpoint itself.
 *
 * @param key            the shared secret a caller sends in {@code X-Api-Key}. Leave it
 *                       unset and one is generated at startup and printed once: the
 *                       endpoint is never open, and a developer can still use it.
 * @param messagesPerWindow how many messages one resident may send per window
 * @param window         how long that window is
 */
@ConfigurationProperties("agent.api")
public record ApiProperties(
        @DefaultValue("") String key,
        @DefaultValue("20") int messagesPerWindow,
        @DefaultValue("1m") Duration window) {}
