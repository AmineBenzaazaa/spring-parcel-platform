package com.msetransport.parcel.events;

import com.msetransport.parcel.events.delivery.DeliveryStatusChangedV1;
import com.msetransport.parcel.events.delivery.DeliveryStatusV1;
import com.msetransport.parcel.events.order.OrderCancelledV1;
import com.msetransport.parcel.events.order.OrderCreatedV1;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCodecTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private final EventCodec codec = new EventCodec(Clock.fixed(NOW, ZoneOffset.UTC));

    private static OrderCreatedV1 orderCreated() {
        return new OrderCreatedV1(UUID.randomUUID(), "PX-ABC123", "M-1001", "Salma", "+212600000001",
                "12 Rue Atlas", "Casablanca", new BigDecimal("249.90"), NOW);
    }

    @Test
    void wrapsPayloadWithTypeVersionAndPartitionKey() {
        OrderCreatedV1 event = orderCreated();

        EventEnvelope envelope = codec.wrap(event, "corr-1");

        assertThat(envelope.eventType()).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.aggregateId()).isEqualTo(event.orderId().toString());
        assertThat(envelope.correlationId()).isEqualTo("corr-1");
        assertThat(envelope.occurredAt()).isEqualTo(NOW);
        assertThat(envelope.payload().has("eventType")).as("metadata not duplicated in payload").isFalse();
    }

    @Test
    void roundTripsOrderCreated() {
        OrderCreatedV1 event = orderCreated();

        EventEnvelope decoded = codec.decode(codec.encode(codec.wrap(event, "corr-1")));

        assertThat(codec.payload(decoded, OrderCreatedV1.class, EventTypes.ORDER_CREATED, 1)).isEqualTo(event);
    }

    @Test
    void roundTripsDeliveryStatusChanged() {
        var event = new DeliveryStatusChangedV1(UUID.randomUUID(), UUID.randomUUID(), "PX-1", UUID.randomUUID(),
                DeliveryStatusV1.ASSIGNED, DeliveryStatusV1.OUT_FOR_DELIVERY, 1, null, "+212600000001",
                BigDecimal.TEN, 3, NOW);

        EventEnvelope decoded = codec.decode(codec.encode(codec.wrap(event, null)));

        assertThat(codec.payload(decoded, DeliveryStatusChangedV1.class, EventTypes.DELIVERY_STATUS_CHANGED, 1))
                .isEqualTo(event);
    }

    @Test
    void ignoresUnknownFieldsAddedByNewerProducers() {
        var envelope = codec.wrap(new OrderCancelledV1(UUID.randomUUID(), "PX-1", "customer", NOW), "c");
        String json = codec.encode(envelope).replace("\"reason\"", "\"newField\":42,\"reason\"");

        var decoded = codec.payload(codec.decode(json), OrderCancelledV1.class, EventTypes.ORDER_CANCELLED, 1);

        assertThat(decoded.reason()).isEqualTo("customer");
    }

    @Test
    void mapsUnknownEnumValuesToUnknown() {
        var event = new DeliveryStatusChangedV1(UUID.randomUUID(), UUID.randomUUID(), "PX-1", null,
                DeliveryStatusV1.ASSIGNED, DeliveryStatusV1.DELIVERED, 1, null, null, BigDecimal.ONE, 1, NOW);
        String json = codec.encode(codec.wrap(event, null)).replace("\"DELIVERED\"", "\"TELEPORTED\"");

        var decoded = codec.payload(codec.decode(json), DeliveryStatusChangedV1.class,
                EventTypes.DELIVERY_STATUS_CHANGED, 1);

        assertThat(decoded.newStatus()).isEqualTo(DeliveryStatusV1.UNKNOWN);
    }

    @Test
    void rejectsMalformedJsonAsInvalidEvent() {
        assertThatThrownBy(() -> codec.decode("{not-json")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> codec.decode("")).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsEnvelopeMissingRequiredMetadata() {
        assertThatThrownBy(() -> codec.decode("{\"eventType\":\"OrderCreated\",\"payload\":{}}"))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void rejectsWrongTypeAndFutureVersions() {
        EventEnvelope envelope = codec.wrap(orderCreated(), null);
        EventEnvelope v2 = new EventEnvelope(envelope.eventId(), envelope.eventType(), 2, envelope.occurredAt(),
                envelope.aggregateId(), null, envelope.payload());

        assertThatThrownBy(() -> codec.payload(envelope, OrderCancelledV1.class, EventTypes.ORDER_CANCELLED, 1))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("Expected OrderCancelled");
        assertThatThrownBy(() -> codec.payload(v2, OrderCreatedV1.class, EventTypes.ORDER_CREATED, 1))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("version 2");
    }

    @Test
    void rejectsPayloadMissingRequiredBusinessFields() {
        EventEnvelope envelope = codec.wrap(orderCreated(), null);
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.payload()).remove("orderId");

        assertThatThrownBy(() -> codec.payload(envelope, OrderCreatedV1.class, EventTypes.ORDER_CREATED, 1))
                .isInstanceOf(InvalidEventException.class);
    }
}
