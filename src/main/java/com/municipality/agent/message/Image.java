package com.municipality.agent.message;

import org.jspecify.annotations.Nullable;

/**
 * A photo. The caption is what the resident typed alongside it and is often
 * absent; what is in the photo itself only becomes readable once a
 * {@link MediaDescriber} has looked at it.
 */
public record Image(String url, @Nullable String caption) implements MessageContent {}
