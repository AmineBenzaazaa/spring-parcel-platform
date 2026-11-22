package com.benzaazaa.parcel.order.application;

import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.order.OrderCancelledV1;
import com.benzaazaa.parcel.events.order.OrderCreatedV1;
import com.benzaazaa.parcel.order.domain.Order;
import com.benzaazaa.parcel.order.domain.OrderRepository;
import com.benzaazaa.parcel.support.messaging.OutboxWriter;
import com.benzaazaa.parcel.support.security.AuthenticatedUser;
import com.benzaazaa.parcel.support.web.NotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final OrderService service = new OrderService(orders, outbox, Clock.systemUTC(), new SimpleMeterRegistry());

    private static final AuthenticatedUser ATLAS = new AuthenticatedUser("merchant.atlas", Set.of("MERCHANT"), "M-1001", null);
    private static final AuthenticatedUser SOUK = new AuthenticatedUser("merchant.souk", Set.of("MERCHANT"), "M-1002", null);
    private static final AuthenticatedUser OPS = new AuthenticatedUser("ops", Set.of("OPS"), null, null);

    private static CreateOrderCommand command(String key) {
        return new CreateOrderCommand("M-1001", "Salma", "+212612345678", "12 Rue Atlas", "Casablanca",
                new BigDecimal("100.00"), key);
    }

    private static Order existingOrder() {
        return Order.create("M-1001", "Salma", "+212612345678", "a", "Casablanca", BigDecimal.TEN, null, Instant.now());
    }

    @Test
    void createSavesOrderAndAppendsOrderCreatedToOutbox() {
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.create(command(null));

        var event = ArgumentCaptor.forClass(OrderCreatedV1.class);
        verify(outbox).append(eq("Order"), eq(Topics.ORDER_EVENTS), event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(order.getId());
        assertThat(event.getValue().codAmount()).isEqualByComparingTo("100.00");
        assertThat(event.getValue().city()).isEqualTo("Casablanca");
    }

    @Test
    void replayWithSameIdempotencyKeyReturnsOriginalWithoutNewEvent() {
        Order original = existingOrder();
        when(orders.findByMerchantIdAndIdempotencyKey("M-1001", "key-1")).thenReturn(Optional.of(original));

        Order result = service.create(command("key-1"));

        assertThat(result).isSameAs(original);
        verify(orders, never()).save(any());
        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void cancelAppendsOrderCancelled() {
        Order order = existingOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        service.cancel(order.getId(), "out of stock", ATLAS);

        var event = ArgumentCaptor.forClass(OrderCancelledV1.class);
        verify(outbox).append(eq("Order"), eq(Topics.ORDER_EVENTS), event.capture());
        assertThat(event.getValue().reason()).isEqualTo("out of stock");
    }

    @Test
    void merchantCannotSeeAnotherMerchantsOrder() {
        Order order = existingOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.get(order.getId(), SOUK)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(order.getId(), OPS)).isSameAs(order);
    }
}
