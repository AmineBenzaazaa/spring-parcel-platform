package com.benzaazaa.parcel.notification.live;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One business event as ops see it on an order's timeline. Id = envelope eventId. */
@Entity
@Table(name = "activity_event")
public class ActivityEvent {

    @Id
    private UUID eventId;
    private UUID orderId;
    private String trackingCode;
    private String eventType;
    private String status;
    private String previousStatus;
    private int attempt;
    private String reason;
    private UUID courierId;
    private String correlationId;
    private Instant occurredAt;
    private Instant receivedAt;

    protected ActivityEvent() {
    }

    public ActivityEvent(UUID eventId, UUID orderId, String trackingCode, String eventType, String status,
                         String previousStatus, int attempt, String reason, UUID courierId, String correlationId,
                         Instant occurredAt, Instant receivedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.trackingCode = trackingCode;
        this.eventType = eventType;
        this.status = status;
        this.previousStatus = previousStatus;
        this.attempt = attempt;
        this.reason = reason;
        this.courierId = courierId;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
