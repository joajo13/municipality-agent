package com.municipality.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/** Receipts in and out, and the sweep that stops them accumulating forever. */
public interface ReceiptRows extends JpaRepository<ReceiptRow, String> {

    @Modifying
    @Query("delete from ReceiptRow row where row.receivedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
