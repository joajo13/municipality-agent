package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;

/**
 * Works out what a resident wants from what they wrote.
 *
 * <p>Deliberately not sealed: the whole point is that the implementation gets replaced.
 * {@link KeywordClassifier} matches words it was told about, {@link ModelClassifier} asks
 * a language model, and nothing downstream can tell which one it is holding.
 *
 * <p>What comes back is a {@link Classification} rather than an {@link Intent}, so that
 * an implementation which costs money per message can say so. One that costs nothing
 * leaves that half empty and nobody has to check.
 */
public interface Classifier {

    Classification classify(NormalizedMessage message);
}
