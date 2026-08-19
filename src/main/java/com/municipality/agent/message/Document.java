package com.municipality.agent.message;

import org.jspecify.annotations.Nullable;

/** An attached file. WhatsApp does not always send the filename. */
public record Document(String url, @Nullable String filename) implements MessageContent {
}
