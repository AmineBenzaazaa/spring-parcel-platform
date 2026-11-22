package com.benzaazaa.parcel.order.messaging;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.order.application.DeliveryStatusProjector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin adapter: decode, then delegate. Exceptions bubble up to the container's error handler
 * (blocking retries, then DLT). The offset is committed only after apply() returns.
 */
@Component
public class DeliveryStatusListener {

    private final EventCodec codec;
    private final DeliveryStatusProjector projector;

    public DeliveryStatusListener(EventCodec codec, DeliveryStatusProjector projector) {
        this.codec = codec;
        this.projector = projector;
    }

    @KafkaListener(id = "order-delivery-status", topics = Topics.DELIVERY_STATUS, groupId = "order-service")
    public void onDeliveryStatus(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = codec.decode(record.value());
        var event = codec.payload(envelope, DeliveryStatusChangedV1.class, EventTypes.DELIVERY_STATUS_CHANGED,
                DeliveryStatusChangedV1.VERSION);
        projector.apply(envelope.eventId(), event);
    }
}
