package com.municipality.agent.policy;

/**
 * What the agent is going to do about a message. Five outcomes and no sixth: act, ask
 * for what is missing, reply, offer a menu, or put a person on.
 *
 * <p>Sealed so that whoever renders these — the console today, a messaging provider
 * later — switches over them without a {@code default}. A new outcome then stops the
 * build at every place that has to say what it looks like, instead of quietly falling
 * into somebody else's branch.
 */
public sealed interface Decision permits StartFlow, AskFor, Answer, FallbackMenu, Handoff {}
