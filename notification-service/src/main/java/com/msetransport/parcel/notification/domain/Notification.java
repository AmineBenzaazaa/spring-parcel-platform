package com.msetransport.parcel.notification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;
    private UUID eventId;
    private UUID orderId;
    private String trackingCode;
    @Enumerated(EnumType.STRING)
    private Channel channel;
    private String recipient;
    private String message;
    @Enumerated(EnumType.STRING)
    private DeliveryResult.Status status;
    private String providerRef;
    private String error;
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID eventId, UUID orderId, String trackingCode, Channel channel, String recipient,
                        String message, DeliveryResult result, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.orderId = orderId;
        this.trackingCode = trackingCode;
        this.channel = channel;
        this.recipient = recipient;
        this.message = message;
        this.status = result.status();
        this.providerRef = result.providerRef();
        this.error = result.error();
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
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

    public Channel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getMessage() {
        return message;
    }

    public DeliveryResult.Status getStatus() {
        return status;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
