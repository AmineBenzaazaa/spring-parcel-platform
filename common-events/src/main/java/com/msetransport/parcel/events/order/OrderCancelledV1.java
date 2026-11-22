package com.benzaazaa.parcel.events.order;

import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.ParcelEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The merchant or ops cancelled an order before it left the warehouse. Version 1. */
public record OrderCancelledV1(
        UUID orderId,
        String trackingCode,
        String reason,
        Instant cancelledAt) implements ParcelEvent {

    public static final int VERSION = 1;

    public OrderCancelledV1 {
        Objects.requireNonNull(orderId, "orderId");
    }

    @Override
    @JsonIgnore
    public String aggregateId() {
        return orderId.toString();
    }

    @Override
    @JsonIgnore
    public String eventType() {
        return EventTypes.ORDER_CANCELLED;
    }

    @Override
    @JsonIgnore
    public int schemaVersion() {
        return VERSION;
    }
}
