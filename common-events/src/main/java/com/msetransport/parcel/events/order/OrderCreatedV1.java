package com.msetransport.parcel.events.order;

import com.msetransport.parcel.events.EventTypes;
import com.msetransport.parcel.events.ParcelEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A merchant created a cash-on-delivery order. Version 1. */
public record OrderCreatedV1(
        UUID orderId,
        String trackingCode,
        String merchantId,
        String recipientName,
        String recipientPhone,
        String addressLine,
        String city,
        BigDecimal codAmount,
        Instant createdAt) implements ParcelEvent {

    public static final int VERSION = 1;

    public OrderCreatedV1 {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(trackingCode, "trackingCode");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(codAmount, "codAmount");
    }

    @Override
    @JsonIgnore
    public String aggregateId() {
        return orderId.toString();
    }

    @Override
    @JsonIgnore
    public String eventType() {
        return EventTypes.ORDER_CREATED;
    }

    @Override
    @JsonIgnore
    public int schemaVersion() {
        return VERSION;
    }
}
