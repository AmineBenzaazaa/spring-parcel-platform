package com.msetransport.parcel.events;

/**
 * Kafka topic names. The ".v1" suffix is the topic contract version: a breaking change
 * to the envelope would go to a new topic while old consumers drain the old one.
 */
public final class Topics {

    /** Order lifecycle events, keyed by orderId. Produced by order-service. */
    public static final String ORDER_EVENTS = "parcel.orders.v1";

    /** Delivery status changes, keyed by orderId. Produced by dispatch-service. */
    public static final String DELIVERY_STATUS = "parcel.delivery-status.v1";

    private Topics() {
    }
}
