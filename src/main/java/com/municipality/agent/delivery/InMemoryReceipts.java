package com.municipality.agent.delivery;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receipts in a map, for a run with nothing behind it. Held to the same promise as the
 * table: a message id written once cannot be written again.
 */
public class InMemoryReceipts implements Receipts {

    private final Map<String, Receipt> remembered = new ConcurrentHashMap<>();

    @Override
    public Optional<Receipt> of(String messageId) {
        return Optional.ofNullable(remembered.get(messageId));
    }

    @Override
    public void remember(Receipt receipt) {
        if (remembered.putIfAbsent(receipt.messageId(), receipt) != null) {
            throw new AlreadyHandled(receipt.messageId());
        }
    }
}
