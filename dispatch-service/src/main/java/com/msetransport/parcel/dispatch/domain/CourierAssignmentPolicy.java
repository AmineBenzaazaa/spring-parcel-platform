package com.msetransport.parcel.dispatch.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Zone-based assignment: among active couriers of the parcel's zone, pick the one with the fewest
 * active deliveries; ties broken by id so the result is deterministic (and testable).
 * Pure function: no Spring, no DB.
 */
public final class CourierAssignmentPolicy {

    private CourierAssignmentPolicy() {
    }

    public static Optional<Courier> choose(String zone, List<Courier> couriers, Map<UUID, Long> activeLoad) {
        if (zone == null) {
            return Optional.empty();
        }
        return couriers.stream()
                .filter(Courier::isActive)
                .filter(c -> zone.equals(c.getZone()))
                .min(Comparator.<Courier>comparingLong(c -> activeLoad.getOrDefault(c.getId(), 0L))
                        .thenComparing(Courier::getId));
    }
}
