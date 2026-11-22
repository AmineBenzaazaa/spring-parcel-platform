package com.msetransport.parcel.dispatch.domain;

/** Result of a state transition; each one becomes a DeliveryStatusChanged event. */
public record StatusChange(DeliveryStatus previous, DeliveryStatus next, int attempt, String reason, long sequence) {
}
