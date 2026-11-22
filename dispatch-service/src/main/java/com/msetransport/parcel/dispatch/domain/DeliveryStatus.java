package com.benzaazaa.parcel.dispatch.domain;

import com.benzaazaa.parcel.events.delivery.DeliveryStatusV1;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Delivery state machine.
 *
 * <pre>
 * PENDING_ASSIGNMENT -> ASSIGNED -> OUT_FOR_DELIVERY -> DELIVERED
 *         |               |              |
 *         v               v              v
 *     CANCELLED       CANCELLED        FAILED --(attempts left)--> OUT_FOR_DELIVERY
 *                                        |
 *                                        +--(max attempts)--> RETURNED   (compensation)
 * </pre>
 */
public enum DeliveryStatus {
    PENDING_ASSIGNMENT,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    RETURNED,
    CANCELLED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED = Map.of(
            PENDING_ASSIGNMENT, EnumSet.of(ASSIGNED, CANCELLED),
            ASSIGNED, EnumSet.of(OUT_FOR_DELIVERY, CANCELLED),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, FAILED),
            FAILED, EnumSet.of(OUT_FOR_DELIVERY, RETURNED),
            DELIVERED, EnumSet.noneOf(DeliveryStatus.class),
            RETURNED, EnumSet.noneOf(DeliveryStatus.class),
            CANCELLED, EnumSet.noneOf(DeliveryStatus.class));

    public boolean canTransitionTo(DeliveryStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** Still occupying a courier (used for load balancing). */
    public static Set<DeliveryStatus> active() {
        return EnumSet.of(ASSIGNED, OUT_FOR_DELIVERY, FAILED);
    }

    public DeliveryStatusV1 toEvent() {
        return DeliveryStatusV1.valueOf(name());
    }
}
