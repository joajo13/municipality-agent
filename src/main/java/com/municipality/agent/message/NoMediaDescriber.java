package com.municipality.agent.message;

import java.util.Optional;

/**
 * Answers nothing about anything. This is what runs until a real model is wired
 * in: media still arrives and still reaches the classifier, announced as a
 * placeholder rather than described.
 */
public class NoMediaDescriber implements MediaDescriber {

    @Override
    public Optional<String> describe(Audio audio) {
        return Optional.empty();
    }

    @Override
    public Optional<String> describe(Image image) {
        return Optional.empty();
    }

    @Override
    public Optional<String> describe(Document document) {
        return Optional.empty();
    }
}
