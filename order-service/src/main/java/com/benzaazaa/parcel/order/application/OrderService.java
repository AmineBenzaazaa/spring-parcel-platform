package com.benzaazaa.parcel.order.application;

import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.order.OrderCancelledV1;
import com.benzaazaa.parcel.events.order.OrderCreatedV1;
import com.benzaazaa.parcel.order.domain.Order;
import com.benzaazaa.parcel.order.domain.OrderRepository;
import com.benzaazaa.parcel.support.messaging.OutboxWriter;
import com.benzaazaa.parcel.support.security.AuthenticatedUser;
import com.benzaazaa.parcel.support.web.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    public static final String AGGREGATE = "Order";
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final Counter created;
    private final Counter cancelled;

    public OrderService(OrderRepository orders, OutboxWriter outbox, Clock clock, MeterRegistry meters) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
        this.created = Counter.builder("parcel.orders.placed").register(meters);
        this.cancelled = Counter.builder("parcel.orders.cancelled").register(meters);
    }

    /**
     * One local transaction: INSERT order + INSERT outbox row. Either both commit or neither does,
     * and Kafka is not touched here at all.
     */
    @Transactional
    public Order create(CreateOrderCommand cmd) {
        if (cmd.idempotencyKey() != null) {
            Optional<Order> existing = orders.findByMerchantIdAndIdempotencyKey(cmd.merchantId(), cmd.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent replay of order {} (key {})", existing.get().getTrackingCode(), cmd.idempotencyKey());
                return existing.get();
            }
        }
        Order order = orders.save(Order.create(cmd.merchantId(), cmd.recipientName(), cmd.recipientPhone(),
                cmd.addressLine(), cmd.city(), cmd.codAmount(), cmd.idempotencyKey(), clock.instant()));
        outbox.append(AGGREGATE, Topics.ORDER_EVENTS, new OrderCreatedV1(order.getId(), order.getTrackingCode(),
                order.getMerchantId(), order.getRecipientName(), order.getRecipientPhone(), order.getAddressLine(),
                order.getCity(), order.getCodAmount(), order.getCreatedAt()));
        created.increment();
        log.info("Order {} created for merchant {} ({} MAD COD)", order.getTrackingCode(), order.getMerchantId(),
                order.getCodAmount());
        return order;
    }

    @Transactional
    public Order cancel(UUID orderId, String reason, AuthenticatedUser user) {
        Order order = getVisible(orderId, user);
        order.cancel(reason, clock.instant());
        outbox.append(AGGREGATE, Topics.ORDER_EVENTS,
                new OrderCancelledV1(order.getId(), order.getTrackingCode(), reason, clock.instant()));
        cancelled.increment();
        log.info("Order {} cancelled: {}", order.getTrackingCode(), reason);
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(UUID orderId, AuthenticatedUser user) {
        return getVisible(orderId, user);
    }

    @Transactional(readOnly = true)
    public Page<Order> list(AuthenticatedUser user, Pageable pageable) {
        return user.isOps() ? orders.findAll(pageable) : orders.findByMerchantId(user.merchantId(), pageable);
    }

    /** A merchant asking for another merchant's order gets 404, not 403: we do not confirm it exists. */
    private Order getVisible(UUID orderId, AuthenticatedUser user) {
        return orders.findById(orderId)
                .filter(o -> user.isOps() || o.isOwnedBy(user.merchantId()))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }
}
