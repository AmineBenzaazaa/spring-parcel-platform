package com.benzaazaa.parcel.notification.live;

import com.benzaazaa.parcel.notification.domain.Notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JSON shapes sent to the dashboard. Kept flat and UI-friendly; they are not the Kafka contracts. */
public final class LivePayloads {

    private LivePayloads() {
    }

    /** {@code type}: ORDER_CREATED or ORDER_CANCELLED. */
    public record OrderLive(UUID eventId, String type, UUID orderId, String trackingCode, String merchantId,
                            String recipientName, String city, BigDecimal codAmount, String reason,
                            String correlationId, Instant occurredAt) {
    }

    public record DeliveryLive(UUID eventId, UUID orderId, String trackingCode, UUID courierId,
                               String previousStatus, String newStatus, int attempt, String reason,
                               BigDecimal codAmount, long sequence, String correlationId, Instant occurredAt) {
    }

    public record NotificationLive(UUID id, UUID orderId, String trackingCode, String channel, String status,
                                   String message, String error, Instant createdAt) {

        public static NotificationLive from(Notification n) {
            return new NotificationLive(n.getId(), n.getOrderId(), n.getTrackingCode(), n.getChannel().name(),
                    n.getStatus().name(), n.getMessage(), n.getError(), n.getCreatedAt());
        }
    }

    public record CircuitLive(String name, String state, float failureRate, int bufferedCalls) {
    }

    public record Hello(String instance, Instant serverTime, CircuitLive circuit) {
    }

    public record TimelineEntry(UUID eventId, String eventType, String status, String previousStatus, int attempt,
                                String reason, UUID courierId, String correlationId, Instant occurredAt) {

        static TimelineEntry from(ActivityEvent e) {
            return new TimelineEntry(e.getEventId(), e.getEventType(), e.getStatus(), e.getPreviousStatus(),
                    e.getAttempt(), e.getReason(), e.getCourierId(), e.getCorrelationId(), e.getOccurredAt());
        }
    }
}
