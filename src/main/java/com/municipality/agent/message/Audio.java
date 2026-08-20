package com.municipality.agent.message;

/**
 * A voice note. It carries no text of its own: whatever the resident said is
 * only readable once a {@link MediaDescriber} has transcribed it.
 */
public record Audio(String url) implements MessageContent {}
