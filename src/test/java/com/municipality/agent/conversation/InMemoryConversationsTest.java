package com.municipality.agent.conversation;

/**
 * The store that runs when nothing is configured, held to the same promises as the one
 * that runs in production.
 */
class InMemoryConversationsTest extends ConversationsContract {

    private final Conversations conversations = new InMemoryConversations();

    @Override
    protected Conversations conversations() {
        return conversations;
    }
}
