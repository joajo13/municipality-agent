package com.municipality.agent.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * What an operator sets about the endpoint itself.
 *
 * @param key            the shared secret a caller sends in {@code X-Api-Key}. Required:
 *                       with nothing here the service does not start.
 * @param allowGeneratedKey makes one up and prints it when none is configured, instead of
 *                       refusing to start. For a local look around, and nowhere else: the
 *                       key it prints goes wherever the logs go.
 * @param messagesPerWindow how many messages one resident may send per window
 * @param window         how long that window is
 * @param maxRequestBytes the largest body this endpoint will read. A message is a line of
 *                       text and a handful of URLs; anything larger is either a mistake
 *                       or somebody finding out what happens.
 */
@ConfigurationProperties("agent.api")
public record ApiProperties(
        @DefaultValue("") String key,
        @DefaultValue("false") boolean allowGeneratedKey,
        @DefaultValue("20") int messagesPerWindow,
        @DefaultValue("1m") Duration window,
        @DefaultValue("65536") int maxRequestBytes) {}
