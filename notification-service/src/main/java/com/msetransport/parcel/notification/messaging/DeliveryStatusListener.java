package com.benzaazaa.parcel.notification.messaging;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.notification.application.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Same topic as order-service, different consumer group: each group gets its own copy (pub/sub). */
@Component
public class DeliveryStatusListener {

    private final EventCodec codec;
    private final NotificationService notifications;

    public DeliveryStatusListener(EventCodec codec, NotificationService notifications) {
        this.codec = codec;
        this.notifications = notifications;
    }

    @KafkaListener(id = "notification-delivery-status", topics = Topics.DELIVERY_STATUS,
            groupId = "notification-service")
    public void onDeliveryStatus(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = codec.decode(record.value());
        notifications.handle(envelope.eventId(), codec.payload(envelope, DeliveryStatusChangedV1.class,
                EventTypes.DELIVERY_STATUS_CHANGED, DeliveryStatusChangedV1.VERSION));
    }
}
