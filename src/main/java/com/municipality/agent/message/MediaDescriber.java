package com.municipality.agent.message;

import java.util.Optional;

/**
 * Turns the media in a message into something readable: a voice note into a
 * transcript, a photo into a description, a file into its contents.
 *
 * <p>Every method returns an {@link Optional} because there is no guarantee an
 * answer exists. Nothing may have run yet, the model may be switched off, or the
 * call may have failed — and none of those are the normaliser's problem. An empty
 * answer simply means "nothing is known about this", and the normaliser falls back
 * to saying that a photo or a voice note was there.
 */
public interface MediaDescriber {

    /** What the resident said in a voice note. */
    Optional<String> describe(Audio audio);

    /** What can be seen in a photo. */
    Optional<String> describe(Image image);

    /** What a file says. */
    Optional<String> describe(Document document);
}
