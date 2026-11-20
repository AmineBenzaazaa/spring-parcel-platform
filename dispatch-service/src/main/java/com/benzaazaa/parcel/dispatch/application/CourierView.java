package com.benzaazaa.parcel.dispatch.application;

import com.benzaazaa.parcel.dispatch.domain.Courier;

import java.util.UUID;

/**
 * A courier as the ops board sees it: identity plus current load.
 *
 * @param activeLoad    deliveries still occupying the courier (assigned, out, failed awaiting retry)
 * @param outForDelivery parcels currently on the road with this courier
 */
public record CourierView(UUID id, String name, String phone, String zone, boolean active, long activeLoad,
                          long outForDelivery, Availability availability) {

    public enum Availability {
        /** Deactivated: never picked by the assignment policy. */
        OFFLINE,
        /** Has at least one parcel on the road. */
        ON_ROUTE,
        /** Has assigned parcels waiting at the hub, none on the road yet. */
        AT_HUB,
        /** Nothing assigned: next in line for a new parcel in the zone. */
        IDLE
    }

    public static CourierView of(Courier c, long activeLoad, long outForDelivery) {
        Availability availability = !c.isActive() ? Availability.OFFLINE
                : outForDelivery > 0 ? Availability.ON_ROUTE
                : activeLoad > 0 ? Availability.AT_HUB
                : Availability.IDLE;
        return new CourierView(c.getId(), c.getName(), c.getPhone(), c.getZone(), c.isActive(), activeLoad,
                outForDelivery, availability);
    }
}
