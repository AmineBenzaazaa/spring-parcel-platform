package com.msetransport.parcel.events.delivery;

import com.msetransport.parcel.events.EventTypes;
import com.msetransport.parcel.events.ParcelEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A delivery moved to a new status. Carries enough data (phone, COD amount) for notification-service
 * to act without calling other services (event-carried state transfer).
 *
 * @param sequence monotonically increasing per delivery; consumers drop stale/out-of-order events.
 */
public record DeliveryStatusChangedV1(
        UUID deliveryId,
        UUID orderId,
        String trackingCode,
        UUID courierId,
        DeliveryStatusV1 previousStatus,
        DeliveryStatusV1 newStatus,
        int attempt,
        String reason,
        String recipientPhone,
        BigDecimal codAmount,
        long sequence,
        Instant changedAt) implements ParcelEvent {

    public static final int VERSION = 1;

    public DeliveryStatusChangedV1 {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(newStatus, "newStatus");
    }

    @Override
    @JsonIgnore
    public String aggregateId() {
        return orderId.toString();
    }

    @Override
    @JsonIgnore
    public String eventType() {
        return EventTypes.DELIVERY_STATUS_CHANGED;
    }

    @Override
    @JsonIgnore
    public int schemaVersion() {
        return VERSION;
    }
}
