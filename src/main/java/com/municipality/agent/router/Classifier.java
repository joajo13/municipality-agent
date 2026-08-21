package com.municipality.agent.router;

import com.municipality.agent.message.NormalizedMessage;

/**
 * Works out what a resident wants from what they wrote.
 *
 * <p>Deliberately not sealed: the whole point is that the implementation gets replaced.
 * {@link KeywordClassifier} matches words it was told about, and a real model takes over
 * in step 5 without anything downstream noticing.
 */
public interface Classifier {

    Intent classify(NormalizedMessage message);
}
