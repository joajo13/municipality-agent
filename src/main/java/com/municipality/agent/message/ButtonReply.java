package com.municipality.agent.message;

/**
 * A tap on one of the buttons the agent offered. The id is routing metadata;
 * the title is the wording the resident actually read before tapping.
 */
public record ButtonReply(String id, String title) implements MessageContent {}
