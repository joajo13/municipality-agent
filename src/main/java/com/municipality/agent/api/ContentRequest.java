package com.municipality.agent.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.municipality.agent.message.Audio;
import com.municipality.agent.message.ButtonReply;
import com.municipality.agent.message.Document;
import com.municipality.agent.message.Image;
import com.municipality.agent.message.Location;
import com.municipality.agent.message.MessageContent;
import com.municipality.agent.message.Text;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * One piece of what arrived, as it comes over the wire.
 *
 * <p>A second set of types that mirror {@code MessageContent} rather than reusing it, and
 * that is the point. What a provider sends is a wire format with a version and a
 * compatibility promise attached to it; what the agent works with is a domain type that
 * should be free to change without breaking anybody's integration. The two meet in
 * {@link #asContent()} and nowhere else.
 *
 * <p>Sealed, so that switch has no {@code default} either: a seventh kind of content
 * cannot be added on one side without the compiler asking about the other.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentRequest.TextContent.class, name = "text"),
        @JsonSubTypes.Type(value = ContentRequest.AudioContent.class, name = "audio"),
        @JsonSubTypes.Type(value = ContentRequest.ImageContent.class, name = "image"),
        @JsonSubTypes.Type(value = ContentRequest.DocumentContent.class, name = "document"),
        @JsonSubTypes.Type(value = ContentRequest.LocationContent.class, name = "location"),
        @JsonSubTypes.Type(value = ContentRequest.ButtonContent.class, name = "button")
})
public sealed interface ContentRequest {

    /** Long enough for anything a person types, short enough that nobody can post a book. */
    int MAX_TEXT = 4096;

    int MAX_URL = 2048;

    MessageContent asContent();

    record TextContent(@NotBlank @Size(max = MAX_TEXT) String body) implements ContentRequest {

        @Override
        public MessageContent asContent() {
            return new Text(body);
        }
    }

    record AudioContent(@NotBlank @Size(max = MAX_URL) String url) implements ContentRequest {

        @Override
        public MessageContent asContent() {
            return new Audio(url);
        }
    }

    record ImageContent(@NotBlank @Size(max = MAX_URL) String url, @Size(max = MAX_TEXT) @Nullable String caption)
            implements ContentRequest {

        @Override
        public MessageContent asContent() {
            return new Image(url, caption);
        }
    }

    record DocumentContent(@NotBlank @Size(max = MAX_URL) String url, @Size(max = 255) @Nullable String filename)
            implements ContentRequest {

        @Override
        public MessageContent asContent() {
            return new Document(url, filename);
        }
    }

    record LocationContent(
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude) implements ContentRequest {

        @Override
        public MessageContent asContent() {
            return new Location(latitude, longitude);
        }
    }

    record ButtonContent(@NotBlank @Size(max = 128) String id, @NotBlank @Size(max = MAX_TEXT) String title)
            implements ContentRequest {

        @Override
        public MessageContent asContent() {
            return new ButtonReply(id, title);
        }
    }
}
