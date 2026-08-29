package com.municipality.agent.persistence;

import com.municipality.agent.delivery.Receipt;
import com.municipality.agent.delivery.Receipts;
import com.municipality.agent.delivery.ReceiptsContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Receipts against the real schema, where the primary key is what actually settles a race. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaReceipts.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaReceiptsTest extends ReceiptsContract {

    @Autowired
    private JpaReceipts receipts;

    @Autowired
    private ReceiptRows rows;

    @Autowired
    private PlatformTransactionManager transactions;

    @Override
    protected Receipts receipts() {
        return receipts;
    }

    @BeforeEach
    void empty() {
        inItsOwnTransaction(rows::deleteAll);
    }

    @Test
    void receiptsNobodyWillRedeliverAreDeletedOutright() {
        receipts.remember(new Receipt("wamid.1", "user-1", NOON, "{}"));

        inItsOwnTransaction(() -> rows.deleteOlderThan(NOON.plus(Duration.ofDays(2))));

        assertThat(receipts.of("wamid.1")).isEmpty();
    }

    @Test
    void receiptsStillWorthKeepingAreLeftAlone() {
        receipts.remember(new Receipt("wamid.1", "user-1", NOON, "{}"));

        inItsOwnTransaction(() -> rows.deleteOlderThan(NOON.minus(Duration.ofDays(1))));

        assertThat(receipts.of("wamid.1")).isPresent();
    }

    private void inItsOwnTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }
}
