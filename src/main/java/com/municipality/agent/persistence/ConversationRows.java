package com.municipality.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Rows in and out. Everything here is either derived by Spring Data from its name or
 * written out in one query — there is no place in this repository for a method whose
 * behaviour has to be explained.
 */
public interface ConversationRows extends JpaRepository<ConversationRow, String> {

    /**
     * Deletes conversations nobody has come back to.
     *
     * <p>The idle timeout already stops old memory being used; this is what stops it
     * being kept. They are two different promises — one about answers, one about how long
     * a document number sits in a table — and only the second one is what a resident
     * would be asking about.
     *
     * @return how many were forgotten
     */
    @Modifying
    @Query("delete from ConversationRow row where row.lastSeen < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
