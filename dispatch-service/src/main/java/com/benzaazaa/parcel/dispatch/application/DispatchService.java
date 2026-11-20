package com.benzaazaa.parcel.dispatch.application;

import com.benzaazaa.parcel.dispatch.domain.Courier;
import com.benzaazaa.parcel.dispatch.domain.CourierAssignmentPolicy;
import com.benzaazaa.parcel.dispatch.domain.CourierRepository;
import com.benzaazaa.parcel.dispatch.domain.Delivery;
import com.benzaazaa.parcel.dispatch.domain.DeliveryRepository;
import com.benzaazaa.parcel.dispatch.domain.DeliveryStatus;
import com.benzaazaa.parcel.dispatch.domain.StatusChange;
import com.benzaazaa.parcel.dispatch.domain.Zones;
import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.events.order.OrderCancelledV1;
import com.benzaazaa.parcel.events.order.OrderCreatedV1;
import com.benzaazaa.parcel.support.messaging.IdempotencyGuard;
import com.benzaazaa.parcel.support.messaging.OutboxWriter;
import com.benzaazaa.parcel.support.security.AuthenticatedUser;
import com.benzaazaa.parcel.support.web.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    public static final String AGGREGATE = "Delivery";
    public static final String CONSUMER_ORDER_EVENTS = "dispatch-service.order-events";
    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final DeliveryRepository deliveries;
    private final CourierRepository couriers;
    private final IdempotencyGuard idempotency;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int maxAttempts;

    public DispatchService(DeliveryRepository deliveries, CourierRepository couriers, IdempotencyGuard idempotency,
                           OutboxWriter outbox, Clock clock, MeterRegistry meters,
                           @Value("${dispatch.max-attempts:3}") int maxAttempts) {
        this.deliveries = deliveries;
        this.couriers = couriers;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
        this.meters = meters;
        this.maxAttempts = maxAttempts;
    }

    // ---------------------------------------------------------------- event handlers (Kafka)

    /**
     * Idempotency check, delivery insert, courier assignment and outbox rows: one DB transaction.
     * If anything throws, everything rolls back and Kafka redelivers (retry topic), so a half-applied
     * event cannot exist.
     */
    @Transactional
    public void onOrderCreated(UUID eventId, OrderCreatedV1 event) {
        if (!idempotency.firstDelivery(eventId, CONSUMER_ORDER_EVENTS)) {
            return;
        }
        if (deliveries.existsByOrderId(event.orderId())) {
            log.info("Delivery for order {} already exists (re-published event {}), skipping", event.trackingCode(), eventId);
            return;
        }
        Optional<String> zone = Zones.forCity(event.city());
        Delivery delivery = Delivery.open(event.orderId(), event.trackingCode(), event.recipientPhone(), event.city(),
                zone.orElse(null), event.codAmount(), maxAttempts, clock.instant());

        List<Courier> candidates = zone.map(couriers::findByZoneAndActiveTrue).orElse(List.of());
        Optional<Courier> courier = CourierAssignmentPolicy.choose(zone.orElse(null), candidates, loadOf(candidates));

        StatusChange change = courier
                .map(c -> delivery.assign(c.getId(), clock.instant()))
                .orElseGet(delivery::pending);
        deliveries.save(delivery);
        publish(delivery, change);
        log.info("Order {} in {} (zone {}) -> {}", event.trackingCode(), event.city(), zone.orElse("UNSERVED"),
                courier.map(c -> "assigned to " + c.getName()).orElse("PENDING_ASSIGNMENT"));
    }

    @Transactional
    public void onOrderCancelled(UUID eventId, OrderCancelledV1 event) {
        if (!idempotency.firstDelivery(eventId, CONSUMER_ORDER_EVENTS)) {
            return;
        }
        deliveries.findByOrderId(event.orderId()).ifPresentOrElse(delivery -> {
            if (delivery.isCancellable()) {
                publish(delivery, delivery.cancel(event.reason(), clock.instant()));
                log.info("Delivery {} cancelled", delivery.getTrackingCode());
            } else {
                // Race: the courier already left. The field outcome (DELIVERED/RETURNED) will win in order-service.
                log.warn("Cancel for {} ignored: delivery is already {}", delivery.getTrackingCode(), delivery.getStatus());
            }
        }, () -> log.warn("OrderCancelled for unknown order {}", event.orderId()));
    }

    // ---------------------------------------------------------------- courier / ops commands (HTTP)

    @Transactional
    public Delivery outForDelivery(UUID orderId, AuthenticatedUser user) {
        Delivery d = forActor(orderId, user);
        publish(d, d.startRound(clock.instant()));
        return d;
    }

    @Transactional
    public Delivery delivered(UUID orderId, AuthenticatedUser user) {
        Delivery d = forActor(orderId, user);
        publish(d, d.markDelivered(clock.instant()));
        return d;
    }

    @Transactional
    public Delivery failed(UUID orderId, String reason, AuthenticatedUser user) {
        Delivery d = forActor(orderId, user);
        d.markFailed(reason, clock.instant()).forEach(change -> publish(d, change));
        return d;
    }

    @Transactional
    public Delivery assign(UUID orderId, UUID courierId) {
        Delivery d = find(orderId);
        Courier courier = couriers.findById(courierId)
                .filter(Courier::isActive)
                .orElseThrow(() -> new NotFoundException("Active courier " + courierId + " not found"));
        publish(d, d.assign(courier.getId(), clock.instant()));
        return d;
    }

    @Transactional(readOnly = true)
    public Delivery get(UUID orderId, AuthenticatedUser user) {
        Delivery d = find(orderId);
        if (!user.isOps() && !d.isAssignedTo(user.courierId())) {
            throw new NotFoundException("Delivery for order " + orderId + " not found");
        }
        return d;
    }

    @Transactional(readOnly = true)
    public List<Delivery> list(AuthenticatedUser user) {
        if (user.isOps()) {
            return deliveries.findTop50ByOrderByUpdatedAtDesc();
        }
        return deliveries.findByCourierIdAndStatusInOrderByUpdatedAtDesc(user.courierId(), DeliveryStatus.active());
    }

    @Transactional(readOnly = true)
    public List<Courier> couriers() {
        return couriers.findAll();
    }

    // ---------------------------------------------------------------- helpers

    private Delivery forActor(UUID orderId, AuthenticatedUser user) {
        Delivery d = find(orderId);
        if (!user.isOps() && !d.isAssignedTo(user.courierId())) {
            throw new AccessDeniedException("Delivery " + d.getTrackingCode() + " is not assigned to you");
        }
        return d;
    }

    private Delivery find(UUID orderId) {
        return deliveries.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Delivery for order " + orderId + " not found"));
    }

    private Map<UUID, Long> loadOf(List<Courier> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        return deliveries.countLoad(candidates.stream().map(Courier::getId).toList(), DeliveryStatus.active())
                .stream().collect(Collectors.toMap(DeliveryRepository.CourierLoad::getCourierId,
                        DeliveryRepository.CourierLoad::getLoad));
    }

    private void publish(Delivery d, StatusChange change) {
        outbox.append(AGGREGATE, Topics.DELIVERY_STATUS, new DeliveryStatusChangedV1(d.getId(), d.getOrderId(),
                d.getTrackingCode(), d.getCourierId(), change.previous() == null ? null : change.previous().toEvent(),
                change.next().toEvent(), change.attempt(), change.reason(), d.getRecipientPhone(), d.getCodAmount(),
                change.sequence(), clock.instant()));
        meters.counter("parcel.deliveries.status.changed", "status", change.next().name()).increment();
    }
}
