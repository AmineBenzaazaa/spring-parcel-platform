package com.msetransport.parcel.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JSON (de)serialisation for {@link EventEnvelope}. Tolerant reader: unknown fields are ignored and
 * unknown enum values map to the {@code UNKNOWN} default, so producers can add fields without
 * breaking older consumers (backward-compatible schema evolution).
 */
public class EventCodec {

    private final ObjectMapper mapper;
    private final Clock clock;

    public EventCodec() {
        this(Clock.systemUTC());
    }

    public EventCodec(Clock clock) {
        this.clock = clock;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                // money must never pass through double: keep COD amounts exact in the JSON tree
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    }

    /** Wraps a payload in a new envelope with a fresh eventId. */
    public EventEnvelope wrap(ParcelEvent event, String correlationId) {
        return new EventEnvelope(
                UUID.randomUUID(),
                event.eventType(),
                event.schemaVersion(),
                Instant.now(clock),
                event.aggregateId(),
                correlationId,
                mapper.valueToTree(event));
    }

    public String encode(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + envelope.eventId(), e);
        }
    }

    public EventEnvelope decode(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidEventException("Empty message");
        }
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidEventException("Malformed event envelope: " + e.getMessage(), e);
        }
    }

    /** Binds the payload to a concrete record after checking the type and version are supported. */
    public <T extends ParcelEvent> T payload(EventEnvelope envelope, Class<T> type, String expectedType, int maxVersion) {
        if (!expectedType.equals(envelope.eventType())) {
            throw new InvalidEventException("Expected " + expectedType + " but got " + envelope.eventType());
        }
        if (envelope.schemaVersion() > maxVersion) {
            throw new InvalidEventException("Unsupported " + expectedType + " schema version " + envelope.schemaVersion());
        }
        try {
            T value = mapper.treeToValue(envelope.payload(), type);
            if (value == null) {
                throw new InvalidEventException("Null payload for " + expectedType);
            }
            return value;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("Cannot bind " + expectedType + " payload", e);
        }
    }

    public JsonNode toTree(Object value) {
        return mapper.valueToTree(value);
    }
}
