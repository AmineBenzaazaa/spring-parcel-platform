package com.benzaazaa.parcel.events;

/**
 * Marker for every event payload. Each payload declares its logical type and schema version,
 * so the envelope can be built without a central registry.
 */
public interface ParcelEvent {

    /** Partition key. All events of one order share a key, so Kafka keeps them in order. */
    String aggregateId();

    String eventType();

    int schemaVersion();
}
