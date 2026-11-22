package com.msetransport.parcel.events.delivery;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Delivery status vocabulary on the wire. UNKNOWN absorbs values added by newer producers. */
public enum DeliveryStatusV1 {
    PENDING_ASSIGNMENT,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    RETURNED,
    CANCELLED,
    @JsonEnumDefaultValue
    UNKNOWN
}
