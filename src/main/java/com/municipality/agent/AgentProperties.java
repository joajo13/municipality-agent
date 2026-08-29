package com.municipality.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Everything about the agent that an operator gets to set, in one place and with a
 * default for each. Nothing here has to be configured for the service to start.
 *
 * @param idleTimeout how long a conversation stays open with nothing said. Past it the
 *                    next message starts again from nothing: a document number given
 *                    yesterday is not permission to file something with it today.
 */
@ConfigurationProperties("agent")
public record AgentProperties(@DefaultValue("30m") Duration idleTimeout) {}
