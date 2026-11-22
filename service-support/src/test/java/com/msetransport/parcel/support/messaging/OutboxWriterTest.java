package com.benzaazaa.parcel.support.messaging;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.order.OrderCancelledV1;
import com.benzaazaa.parcel.support.correlation.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxWriterTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final EventCodec codec = new EventCodec();
    private final OutboxWriter writer = new OutboxWriter(repository, codec);

    @AfterEach
    void clear() {
        CorrelationId.clear();
    }

    @Test
    void storesTheFullEnvelopeKeyedByAggregateWithCurrentCorrelationId() {
        CorrelationId.set("corr-7");
        UUID orderId = UUID.randomUUID();

        UUID eventId = writer.append("Order", "parcel.orders.v1",
                new OrderCancelledV1(orderId, "PX-1", "merchant request", Instant.now()));

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent row = captor.getValue();
        assertThat(row.getId()).isEqualTo(eventId);
        assertThat(row.getAggregateId()).isEqualTo(orderId.toString());
        assertThat(row.getEventType()).isEqualTo(EventTypes.ORDER_CANCELLED);
        assertThat(row.getTopic()).isEqualTo("parcel.orders.v1");
        assertThat(row.getCorrelationId()).isEqualTo("corr-7");
        assertThat(row.isNew()).isTrue();
        assertThat(codec.decode(row.getPayload()).eventId()).isEqualTo(eventId);
    }

    @Test
    void createsACorrelationIdWhenNoneIsActive() {
        writer.append("Order", "t", new OrderCancelledV1(UUID.randomUUID(), "PX-1", null, Instant.now()));

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isNotBlank();
    }
}
