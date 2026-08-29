package com.municipality.agent.api;

import com.municipality.agent.Outcome;
import com.municipality.agent.console.DecisionRenderer;
import com.municipality.agent.delivery.Receipt;
import com.municipality.agent.delivery.Receipts;
import com.municipality.agent.observability.Turns;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The one endpoint: a message in, what the agent did about it out.
 *
 * <p>It is a channel adapter and nothing else. Reading a request, deciding a message has
 * already been answered, and turning an outcome back into JSON — everything that actually
 * decides anything is behind {@link Turns}, which the console calls in exactly the same
 * way.
 *
 * <p>Handling is at-most-once against the message id. A provider that redelivers gets the
 * answer it was given the first time rather than a second turn, and the header says which
 * of the two it is looking at.
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    /** Tells the caller this is the answer from last time, not a new one. */
    static final String REPLAY = "X-Idempotent-Replay";

    static final String RETRY_AFTER = "Retry-After";

    private final Turns turns;
    private final Receipts receipts;
    private final RateLimiter limiter;
    private final JsonMapper json;
    private final Clock clock;
    private final DecisionRenderer renderer = new DecisionRenderer();

    public MessageController(
            Turns turns, Receipts receipts, RateLimiter limiter, JsonMapper json, Clock clock) {

        this.turns = turns;
        this.receipts = receipts;
        this.limiter = limiter;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<TurnResponse> handle(@Valid @RequestBody MessageRequest request) {
        String messageId = request.idOrGenerated();

        Optional<TurnResponse> alreadyAnswered = answerFromLastTime(messageId);

        if (alreadyAnswered.isPresent()) {
            return ResponseEntity.ok().header(REPLAY, "true").body(alreadyAnswered.get());
        }

        Instant now = clock.instant();

        if (!limiter.allows(request.from(), now)) throw new TooManyMessages();

        Outcome outcome = turns.handle(request.asIncoming(messageId, now));
        TurnResponse response = TurnResponse.of(outcome, renderer);

        return ResponseEntity.status(HttpStatus.OK)
                .header(REPLAY, String.valueOf(!remembered(messageId, request.from(), now, response)))
                .body(answerFromLastTime(messageId).orElse(response));
    }

    /**
     * @return whether this turn's own answer is the one that was written. It is not, when
     *         another instance answered the same delivery first — in which case theirs is
     *         the answer the caller gets, because two answers to one message id is the
     *         thing this is here to prevent.
     */
    private boolean remembered(String messageId, String userId, Instant now, TurnResponse response) {
        try {
            receipts.remember(new Receipt(messageId, userId, now, asJson(response)));
            return true;
        } catch (Receipts.AlreadyHandled somebodyGotThereFirst) {
            log.info("Message {} was answered by another delivery; returning that answer.", messageId);
            return false;
        }
    }

    private Optional<TurnResponse> answerFromLastTime(String messageId) {
        return receipts.of(messageId).map(Receipt::response).map(this::fromJson);
    }


    private String asJson(TurnResponse response) {
        return json.writeValueAsString(response);
    }

    /**
     * A receipt written by an older version of this service can be a shape this one does
     * not read. That is not worth failing a redelivery over: the honest thing is to say
     * nothing was found and answer the message again.
     */
    private @Nullable TurnResponse fromJson(String stored) {
        try {
            return json.readValue(stored, TurnResponse.class);
        } catch (JacksonException fromAnotherVersion) {
            log.warn("A stored receipt could not be read back and will be answered again: {}",
                    fromAnotherVersion.getMessage());
            return null;
        }
    }

    /** One resident, too many messages, too fast. */
    static class TooManyMessages extends RuntimeException {}
}
