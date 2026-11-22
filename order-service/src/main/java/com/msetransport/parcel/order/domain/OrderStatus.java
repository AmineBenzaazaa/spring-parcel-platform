package com.msetransport.parcel.order.domain;

/** Order status as seen by the merchant. Delivery statuses come from dispatch-service events. */
public enum OrderStatus {
    CREATED,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    RETURNED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED || this == RETURNED || this == CANCELLED;
    }

    /** Only parcels still in the warehouse can be cancelled; once a courier has it, it is too late. */
    public boolean isCancellable() {
        return this == CREATED || this == ASSIGNED;
    }
}
