package com.municipality.agent.persistence;

import com.municipality.agent.delivery.Receipt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One handled message as a row. */
@Entity
@Table(name = "message_receipt")
public class ReceiptRow {

    @Id
    @Column(name = "message_id", nullable = false, length = 128)
    private String messageId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /**
     * The answer, as JSON. Not a large object: a response is a few hundred bytes, and a
     * column with a size on it is a column the database can keep on the row and the
     * schema check can agree about across two engines.
     */
    @Column(name = "response", nullable = false, length = 8192)
    private String response;

    protected ReceiptRow() {}

    public static ReceiptRow from(Receipt receipt) {
        var row = new ReceiptRow();
        row.messageId = receipt.messageId();
        row.userId = receipt.userId();
        row.receivedAt = receipt.receivedAt();
        row.response = receipt.response();

        return row;
    }

    public Receipt asReceipt() {
        return new Receipt(messageId, userId, receivedAt, response);
    }
}
