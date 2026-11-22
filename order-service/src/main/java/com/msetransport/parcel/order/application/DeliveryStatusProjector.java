package com.benzaazaa.parcel.order.application;

import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.order.domain.OrderRepository;
import com.benzaazaa.parcel.support.messaging.IdempotencyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Keeps the merchant-facing order status in sync with dispatch-service (read-side projection). */
@Service
public class DeliveryStatusProjector {

    public static final String CONSUMER = "order-service.delivery-status";
    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusProjector.class);

    private final OrderRepository orders;
    private final IdempotencyGuard idempotency;
    private final Clock clock;

    public DeliveryStatusProjector(OrderRepository orders, IdempotencyGuard idempotency, Clock clock) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional
    public void apply(UUID eventId, DeliveryStatusChangedV1 event) {
        if (!idempotency.firstDelivery(eventId, CONSUMER)) {
            return;
        }
        orders.findById(event.orderId()).ifPresentOrElse(order -> {
            boolean changed = order.applyDeliveryStatus(event.newStatus(), event.sequence(), event.attempt(),
                    clock.instant());
            log.info("Order {} delivery status {} (seq {}) -> order status {}{}", order.getTrackingCode(),
                    event.newStatus(), event.sequence(), order.getStatus(), changed ? "" : " (ignored)");
        }, () -> log.warn("DeliveryStatusChanged for unknown order {}", event.orderId()));
    }
}
