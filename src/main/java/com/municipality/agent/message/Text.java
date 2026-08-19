package com.municipality.agent.message;

/** What the resident typed. Line breaks they typed are part of it. */
public record Text(String body) implements MessageContent {
}
