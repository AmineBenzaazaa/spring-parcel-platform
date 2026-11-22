package com.benzaazaa.parcel.events;

/** Kafka record header names. Headers mirror envelope fields so infra can route without parsing JSON. */
public final class EventHeaders {

    public static final String EVENT_ID = "x-event-id";
    public static final String EVENT_TYPE = "x-event-type";
    public static final String SCHEMA_VERSION = "x-schema-version";
    public static final String CORRELATION_ID = "x-correlation-id";

    private EventHeaders() {
    }
}
