package com.municipality.agent.message;

/** A pin the resident shared, typically to place a complaint on a street. */
public record Location(double latitude, double longitude) implements MessageContent {
}
