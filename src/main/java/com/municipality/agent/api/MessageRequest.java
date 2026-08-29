package com.municipality.agent.api;

import com.municipality.agent.message.IncomingMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A message as a provider posts it.
 *
 * <p>Two of the four fields are optional and both defaults are deliberate. A missing
 * {@code messageId} gets one generated, which costs the caller the ability to retry
 * safely — the id is what makes a redelivery a redelivery rather than a second message.
 * A missing {@code sentAt} is taken as now, which is right for a provider posting
 * immediately and wrong for one replaying a backlog, so a provider that replays should
 * send it.
 *
 * @param messageId the provider's own id for this message. Send the same one again and
 *                  the same answer comes back without the turn happening twice.
 * @param from      whoever sent it, as the channel identifies them
 */
public record MessageRequest(
        @Size(max = 128) @Nullable String messageId,
        @NotBlank @Size(max = 128) String from,
        @Nullable Instant sentAt,
        @NotEmpty @Size(max = 10) List<@Valid ContentRequest> contents) {

    public String idOrGenerated() {
        return messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId;
    }

    public IncomingMessage asIncoming(String messageId, Instant now) {
        return new IncomingMessage(
                messageId,
                from,
                sentAt == null ? now : sentAt,
                contents.stream().map(ContentRequest::asContent).toList());
    }
}
