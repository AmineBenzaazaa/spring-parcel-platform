package com.msetransport.parcel.notification.application;

import com.msetransport.parcel.events.delivery.DeliveryStatusChangedV1;
import com.msetransport.parcel.notification.domain.DeliveryResult;
import com.msetransport.parcel.notification.domain.MessageTemplates;
import com.msetransport.parcel.notification.domain.Notification;
import com.msetransport.parcel.notification.domain.NotificationRepository;
import com.msetransport.parcel.notification.sender.NotificationSender;
import com.msetransport.parcel.notification.sender.OutgoingMessage;
import com.msetransport.parcel.support.messaging.IdempotencyGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    public static final String CONSUMER = "notification-service.delivery-status";

    private final List<NotificationSender> senders;
    private final NotificationRepository notifications;
    private final IdempotencyGuard idempotency;
    private final Clock clock;
    private final MeterRegistry meters;
    private final ApplicationEventPublisher events;

    public NotificationService(List<NotificationSender> senders, NotificationRepository notifications,
                               IdempotencyGuard idempotency, Clock clock, MeterRegistry meters) {
        this(senders, notifications, idempotency, clock, meters, event -> { });
    }

    @Autowired
    public NotificationService(List<NotificationSender> senders, NotificationRepository notifications,
                               IdempotencyGuard idempotency, Clock clock, MeterRegistry meters,
                               ApplicationEventPublisher events) {
        this.senders = senders;
        this.notifications = notifications;
        this.idempotency = idempotency;
        this.clock = clock;
        this.meters = meters;
        this.events = events;
    }

    /**
     * Dedup + send + record in one transaction. Trade-off: the SMS call happens inside the DB
     * transaction (bounded by the 2s TimeLimiter x 3 retries). A crash after sending but before
     * commit means the SMS may be sent twice: notifications are at-least-once, which is acceptable
     * for "your parcel is on its way". Senders never throw, so a broken provider cannot cause
     * redelivery storms.
     */
    @Transactional
    public void handle(UUID eventId, DeliveryStatusChangedV1 event) {
        if (!idempotency.firstDelivery(eventId, CONSUMER)) {
            return;
        }
        Optional<String> text = MessageTemplates.render(event);
        if (text.isEmpty()) {
            return;
        }
        var message = new OutgoingMessage(event.orderId(), event.trackingCode(), event.recipientPhone(), text.get());
        for (NotificationSender sender : senders) {
            DeliveryResult result = sender.send(message);
            Notification saved = notifications.save(new Notification(eventId, event.orderId(), event.trackingCode(),
                    sender.channel(), event.recipientPhone(), text.get(), result, clock.instant()));
            events.publishEvent(new NotificationRecorded(saved));
            meters.counter("parcel.notifications", "channel", sender.channel().name(),
                    "result", result.status().name()).increment();
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> forOrder(UUID orderId) {
        return notifications.findByOrderIdOrderByCreatedAt(orderId);
    }

    @Transactional(readOnly = true)
    public List<Notification> latest() {
        return notifications.findTop50ByOrderByCreatedAtDesc();
    }
}
