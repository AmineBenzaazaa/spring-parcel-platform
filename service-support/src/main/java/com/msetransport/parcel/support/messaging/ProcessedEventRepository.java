package com.benzaazaa.parcel.support.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /**
     * Atomic "claim" of an event id. Returns 1 the first time, 0 for a duplicate. Two concurrent
     * deliveries of the same event serialise on the primary key: the second waits, then gets 0.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO processed_event (event_id, consumer, processed_at)
            VALUES (:eventId, :consumer, :at)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("consumer") String consumer, @Param("at") Instant at);

    long countByIdConsumer(String consumer);
}
