package com.benzaazaa.parcel.events;

/** Logical event type names (stable, independent of Java class names). */
public final class EventTypes {

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String DELIVERY_STATUS_CHANGED = "DeliveryStatusChanged";

    private EventTypes() {
    }
}
