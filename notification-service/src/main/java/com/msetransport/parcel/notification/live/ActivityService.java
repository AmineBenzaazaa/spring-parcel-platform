package com.benzaazaa.parcel.notification.live;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.events.order.OrderCancelledV1;
import com.benzaazaa.parcel.events.order.OrderCreatedV1;
import com.benzaazaa.parcel.notification.live.LivePayloads.DeliveryLive;
import com.benzaazaa.parcel.notification.live.LivePayloads.OrderLive;
import com.benzaazaa.parcel.notification.live.LivePayloads.TimelineEntry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns Kafka envelopes into (1) a stored timeline row and (2) the live event to push. Returns empty
 * for event types the dashboard does not show and for duplicates, so a redelivered event is never
 * pushed twice.
 */
@Service
public class ActivityService {

    private final ActivityEventRepository events;
    private final EventCodec codec;
    private final Clock clock;

    public ActivityService(ActivityEventRepository events, EventCodec codec, Clock clock) {
        this.events = events;
        this.codec = codec;
        this.clock = clock;
    }

    @Transactional
    public Optional<LiveEvent> record(EventEnvelope envelope) {
        Optional<Mapped> mapped = map(envelope);
        if (mapped.isEmpty() || events.insertIfAbsent(mapped.get().row()) == 0) {
            return Optional.empty();
        }
        return Optional.of(mapped.get().live());
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID orderId) {
        return events.findByOrderIdOrderByOccurredAtAscReceivedAtAsc(orderId).stream()
                .map(TimelineEntry::from).toList();
    }

    /** The timeline is an ops convenience, not an audit log: keep a week. */
    @Scheduled(cron = "${parcel.live.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeOldActivity() {
        events.deleteOlderThan(clock.instant().minus(Duration.ofDays(7)));
    }

    private record Mapped(ActivityEvent row, LiveEvent live) {
    }

    private Optional<Mapped> map(EventEnvelope env) {
        String corr = env.correlationId();
        return switch (env.eventType()) {
            case EventTypes.ORDER_CREATED -> {
                var e = codec.payload(env, OrderCreatedV1.class, EventTypes.ORDER_CREATED, OrderCreatedV1.VERSION);
                var row = new ActivityEvent(env.eventId(), e.orderId(), e.trackingCode(), "ORDER_CREATED", "CREATED",
                        null, 0, null, null, corr, env.occurredAt(), clock.instant());
                yield Optional.of(new Mapped(row, new LiveEvent(LiveEvent.ORDER, new OrderLive(env.eventId(),
                        "ORDER_CREATED", e.orderId(), e.trackingCode(), e.merchantId(), e.recipientName(), e.city(),
                        e.codAmount(), null, corr, env.occurredAt()))));
            }
            case EventTypes.ORDER_CANCELLED -> {
                var e = codec.payload(env, OrderCancelledV1.class, EventTypes.ORDER_CANCELLED,
                        OrderCancelledV1.VERSION);
                var row = new ActivityEvent(env.eventId(), e.orderId(), e.trackingCode(), "ORDER_CANCELLED",
                        "CANCELLED", null, 0, e.reason(), null, corr, env.occurredAt(), clock.instant());
                yield Optional.of(new Mapped(row, new LiveEvent(LiveEvent.ORDER, new OrderLive(env.eventId(),
                        "ORDER_CANCELLED", e.orderId(), e.trackingCode(), null, null, null, null, e.reason(), corr,
                        env.occurredAt()))));
            }
            case EventTypes.DELIVERY_STATUS_CHANGED -> {
                var e = codec.payload(env, DeliveryStatusChangedV1.class, EventTypes.DELIVERY_STATUS_CHANGED,
                        DeliveryStatusChangedV1.VERSION);
                String previous = e.previousStatus() == null ? null : e.previousStatus().name();
                var row = new ActivityEvent(env.eventId(), e.orderId(), e.trackingCode(), "DELIVERY_STATUS_CHANGED",
                        e.newStatus().name(), previous, e.attempt(), e.reason(), e.courierId(), corr,
                        env.occurredAt(), clock.instant());
                yield Optional.of(new Mapped(row, new LiveEvent(LiveEvent.DELIVERY, new DeliveryLive(env.eventId(),
                        e.orderId(), e.trackingCode(), e.courierId(), previous, e.newStatus().name(), e.attempt(),
                        e.reason(), e.codAmount(), e.sequence(), corr, env.occurredAt()))));
            }
            default -> Optional.empty();
        };
    }
}
