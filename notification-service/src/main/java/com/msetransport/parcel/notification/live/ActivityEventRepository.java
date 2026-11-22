package com.msetransport.parcel.notification.live;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    List<ActivityEvent> findByOrderIdOrderByOccurredAtAscReceivedAtAsc(UUID orderId);

    /** Insert-if-absent: returns 0 for a redelivered event (same eventId), so it is not broadcast twice. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO activity_event (event_id, order_id, tracking_code, event_type, status, previous_status,
                                        attempt, reason, courier_id, correlation_id, occurred_at, received_at)
            VALUES (:#{#e.eventId}, :#{#e.orderId}, :#{#e.trackingCode}, :#{#e.eventType}, :#{#e.status},
                    :#{#e.previousStatus}, :#{#e.attempt}, :#{#e.reason}, :#{#e.courierId}, :#{#e.correlationId},
                    :#{#e.occurredAt}, :#{#e.receivedAt})
            ON CONFLICT (event_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("e") ActivityEvent e);

    @Modifying
    @Query("DELETE FROM ActivityEvent a WHERE a.occurredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
