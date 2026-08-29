package com.municipality.agent.api;

import com.municipality.agent.conversation.ConcurrentTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/**
 * Every way this endpoint can say no, in one shape.
 *
 * <p>Problem details rather than a bespoke error object, and a type URI on each, so that
 * a caller can branch on something stable instead of matching on prose.
 *
 * <p>What is in a message never appears in a response. A validation failure names the
 * field and what was wrong with it, not what was in it: the body of a message is a
 * resident talking, and echoing it back puts it in the caller's logs as well as ours.
 */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    private static final String PROBLEMS = "https://municipality-agent/problems/";

    /**
     * The same resident had two messages in flight and this one lost.
     *
     * <p>409 rather than 500, because nothing is broken and the caller has something
     * useful to do: send it again. It will be handled against the conversation as it
     * stands by then.
     */
    @ExceptionHandler(ConcurrentTurn.class)
    ResponseEntity<ProblemDetail> whenTwoTurnsRace(ConcurrentTurn lost) {
        log.info("A turn lost a race and is being handed back to the caller: {}", lost.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(
                HttpStatus.CONFLICT,
                "conversation-moved-on",
                "Conversation moved on",
                "Another message from this resident was handled first. Send this one again."));
    }

    @ExceptionHandler(MessageController.TooManyMessages.class)
    ResponseEntity<ProblemDetail> whenTooManyMessages(MessageController.TooManyMessages ignored) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(MessageController.RETRY_AFTER, "60")
                .body(problem(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "too-many-messages",
                        "Too many messages",
                        "This resident has sent more messages than are allowed in this window."));
    }

    /** A body that is not shaped like a message at all: bad JSON, an unknown content type. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException failure,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST,
                "unreadable-request",
                "Unreadable request",
                "The body is not a message this endpoint can read."));
    }

    /** A body of the right shape with the wrong things in it. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException failure,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                "One or more fields are not acceptable.");

        List<String> fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();

        problem.setProperty("fields", fields);

        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(PROBLEMS + type));
        problem.setTitle(title);
        problem.setDetail(detail);

        return problem;
    }
}
