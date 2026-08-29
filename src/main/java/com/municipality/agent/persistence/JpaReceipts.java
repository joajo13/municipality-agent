package com.municipality.agent.persistence;

import com.municipality.agent.delivery.Receipt;
import com.municipality.agent.delivery.Receipts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Receipts in a table, which is the only place they are worth keeping: a redelivery can
 * land on any instance, and an instance that has never seen the first delivery is exactly
 * the one most likely to get the second.
 */
@Repository
@ConditionalOnProperty(name = "agent.store", havingValue = "jpa", matchIfMissing = true)
public class JpaReceipts implements Receipts {

    private final ReceiptRows rows;

    public JpaReceipts(ReceiptRows rows) {
        this.rows = rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Receipt> of(String messageId) {
        return rows.findById(messageId).map(ReceiptRow::asReceipt);
    }

    @Override
    @Transactional
    public void remember(Receipt receipt) {
        if (rows.existsById(receipt.messageId())) throw new AlreadyHandled(receipt.messageId());

        try {
            rows.saveAndFlush(ReceiptRow.from(receipt));
        } catch (DataIntegrityViolationException somebodyGotThereFirst) {
            // Two deliveries of the same message, handled at once by two instances. The
            // primary key is what settles it, and this is the one that lost.
            throw new AlreadyHandled(receipt.messageId());
        }
    }
}
