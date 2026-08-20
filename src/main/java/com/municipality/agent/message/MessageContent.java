package com.municipality.agent.message;

/**
 * One piece of what a resident sent. A single message can carry several: a voice
 * note and then a line of text, a photo with a caption, a shared location.
 *
 * <p>The interface is {@code sealed}, so this list is the whole list — nobody
 * outside this package can add a seventh kind. That is what lets every consumer
 * switch over it without a {@code default} branch: adding a variant here turns
 * into a compile error at each site that has to handle it, instead of a silent
 * fallthrough at runtime.
 */
public sealed interface MessageContent permits Text, Audio, Image, Document, Location, ButtonReply {
}
