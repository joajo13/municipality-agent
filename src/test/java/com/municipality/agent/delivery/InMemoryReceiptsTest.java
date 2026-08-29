package com.municipality.agent.delivery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Receipts in a map, held to the same promise as the table. */
class InMemoryReceiptsTest extends ReceiptsContract {

    private final Receipts receipts = new InMemoryReceipts();

    @Override
    protected Receipts receipts() {
        return receipts;
    }

    @Test
    void aReceiptWithoutAMessageOrASenderIsNotAReceipt() {
        assertThatThrownBy(() -> new Receipt(" ", "user-1", NOON, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Receipt("wamid.1", " ", NOON, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Receipt("wamid.1", "user-1", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Receipt("wamid.1", "user-1", NOON, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
