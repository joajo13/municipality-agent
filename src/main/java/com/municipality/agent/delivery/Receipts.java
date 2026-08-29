package com.municipality.agent.delivery;

import java.util.Optional;

/**
 * What has already been handled.
 *
 * <p>Two methods and one promise: a message id that was written once cannot be written
 * again. {@link #remember} says so by failing rather than by overwriting, because the
 * caller that loses has an answer to fetch and return, not an error to report.
 */
public interface Receipts {

    Optional<Receipt> of(String messageId);

    /**
     * @throws AlreadyHandled when this message id was already written, by this instance or another
     */
    void remember(Receipt receipt);

    /** Raised when a message that was already answered is answered again. */
    class AlreadyHandled extends RuntimeException {

        public AlreadyHandled(String messageId) {
            super("Message " + messageId + " has already been handled");
        }
    }
}
