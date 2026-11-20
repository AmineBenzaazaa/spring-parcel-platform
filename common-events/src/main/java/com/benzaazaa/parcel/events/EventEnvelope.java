package com.benzaazaa.parcel.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Wire format of every message: metadata + raw JSON payload.
 *
 * <p>The payload stays a {@link JsonNode} until the consumer knows the type, so a consumer can
 * read {@code eventType}/{@code schemaVersion} first and pick the right record (or upcast an old
 * version) before binding. {@code eventId} is the idempotency key used by consumers.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        JsonNode payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
    }
}
