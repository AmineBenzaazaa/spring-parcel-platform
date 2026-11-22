package com.msetransport.parcel.order.application;

import com.msetransport.parcel.events.Topics;
import com.msetransport.parcel.events.order.OrderCancelledV1;
import com.msetransport.parcel.events.order.OrderCreatedV1;
import com.msetransport.parcel.order.domain.Order;
import com.msetransport.parcel.order.domain.OrderRepository;
import com.msetransport.parcel.order.domain.OrderStatus;
import com.msetransport.parcel.support.messaging.OutboxWriter;
import com.msetransport.parcel.support.security.AuthenticatedUser;
import com.msetransport.parcel.support.web.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    public static final String AGGREGATE = "Order";
    /** "Today" for KPIs is a Moroccan business day, whatever the server's time zone. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Casablanca");
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

    /** Newest first; an empty status filter means every status. Merchants only see their own orders. */
    @Transactional(readOnly = true)
    public Page<Order> list(AuthenticatedUser user, Collection<OrderStatus> statuses, Pageable pageable) {
        boolean filtered = statuses != null && !statuses.isEmpty();
        if (user.isOps()) {
            return filtered ? orders.findByStatusIn(statuses, pageable) : orders.findAll(pageable);
        }
        return filtered ? orders.findByMerchantIdAndStatusIn(user.merchantId(), statuses, pageable)
                : orders.findByMerchantId(user.merchantId(), pageable);
    }

    @Transactional(readOnly = true)
    public OrderStats stats(AuthenticatedUser user) {
        var since = LocalDate.now(clock.withZone(BUSINESS_ZONE)).atStartOfDay(BUSINESS_ZONE).toInstant();
        var rows = orders.statusStats(since, user.isOps() ? "" : user.merchantId()).stream()
                .map(r -> new OrderStats.StatusRow(OrderStatus.valueOf((String) r[0]), ((Number) r[1]).longValue(),
                        ((Number) r[2]).longValue(), ((Number) r[3]).longValue(), new BigDecimal(r[4].toString())))
                .toList();
        return OrderStats.from(since, rows);
    }

    /** A merchant asking for another merchant's order gets 404, not 403: we do not confirm it exists. */
    private Order getVisible(UUID orderId, AuthenticatedUser user) {
        return orders.findById(orderId)
                .filter(o -> user.isOps() || o.isOwnedBy(user.merchantId()))
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }
}
