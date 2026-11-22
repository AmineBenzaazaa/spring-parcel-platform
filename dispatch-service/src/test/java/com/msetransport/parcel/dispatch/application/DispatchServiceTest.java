package com.msetransport.parcel.dispatch.application;

import com.msetransport.parcel.dispatch.domain.Courier;
import com.msetransport.parcel.dispatch.domain.CourierRepository;
import com.msetransport.parcel.dispatch.domain.Delivery;
import com.msetransport.parcel.dispatch.domain.DeliveryRepository;
import com.msetransport.parcel.dispatch.domain.DeliveryStatus;
import com.msetransport.parcel.events.Topics;
import com.msetransport.parcel.events.delivery.DeliveryStatusChangedV1;
import com.msetransport.parcel.events.delivery.DeliveryStatusV1;
import com.msetransport.parcel.events.order.OrderCancelledV1;
import com.msetransport.parcel.events.order.OrderCreatedV1;
import com.msetransport.parcel.support.messaging.IdempotencyGuard;
import com.msetransport.parcel.support.messaging.OutboxWriter;
import com.msetransport.parcel.support.security.AuthenticatedUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DispatchServiceTest {

    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final CourierRepository couriers = mock(CourierRepository.class);
    private final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final DispatchService service = new DispatchService(deliveries, couriers, guard, outbox,
            Clock.systemUTC(), new SimpleMeterRegistry(), 3);

    private static final Courier YOUSSEF = new Courier(UUID.randomUUID(), "Youssef", "+212661000001", "CASA", true);

    private static OrderCreatedV1 orderIn(String city) {
        return new OrderCreatedV1(UUID.randomUUID(), "PX-ABCDEFGH", "M-1001", "Salma", "+212612345678", "addr",
                city, new BigDecimal("150.00"), Instant.now());
    }

    private DeliveryStatusChangedV1 publishedEvent() {
        var captor = ArgumentCaptor.forClass(DeliveryStatusChangedV1.class);
        verify(outbox).append(eq("Delivery"), eq(Topics.DELIVERY_STATUS), captor.capture());
        return captor.getValue();
    }

    @Test
    void orderInServedZoneIsAssignedAndAssignedEventPublished() {
        UUID eventId = UUID.randomUUID();
        when(guard.firstDelivery(eventId, DispatchService.CONSUMER_ORDER_EVENTS)).thenReturn(true);
        when(couriers.findByZoneAndActiveTrue("CASA")).thenReturn(List.of(YOUSSEF));
        when(deliveries.countLoad(any(), any())).thenReturn(List.of());
        OrderCreatedV1 order = orderIn("Casablanca");

        service.onOrderCreated(eventId, order);

        DeliveryStatusChangedV1 event = publishedEvent();
        assertThat(event.newStatus()).isEqualTo(DeliveryStatusV1.ASSIGNED);
        assertThat(event.courierId()).isEqualTo(YOUSSEF.getId());
        assertThat(event.orderId()).isEqualTo(order.orderId());
        assertThat(event.codAmount()).isEqualByComparingTo("150.00");
        assertThat(event.sequence()).isEqualTo(1);
        verify(deliveries).save(any(Delivery.class));
    }

    @Test
    void duplicateEventHasNoEffect() {
        UUID eventId = UUID.randomUUID();
        when(guard.firstDelivery(eventId, DispatchService.CONSUMER_ORDER_EVENTS)).thenReturn(false);

        service.onOrderCreated(eventId, orderIn("Casablanca"));

        verify(deliveries, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void unservedCityStaysPendingAssignment() {
        when(guard.firstDelivery(any(), anyString())).thenReturn(true);

        service.onOrderCreated(UUID.randomUUID(), orderIn("Agadir"));

        assertThat(publishedEvent().newStatus()).isEqualTo(DeliveryStatusV1.PENDING_ASSIGNMENT);
    }

    @Test
    void courierCannotDriveSomeoneElsesDelivery() {
        Delivery d = Delivery.open(UUID.randomUUID(), "PX-1", null, "Casablanca", "CASA", BigDecimal.ONE, 3, Instant.now());
        d.assign(YOUSSEF.getId(), Instant.now());
        when(deliveries.findByOrderId(d.getOrderId())).thenReturn(Optional.of(d));
        var otherCourier = new AuthenticatedUser("courier.casa2", Set.of("COURIER"), null, UUID.randomUUID());

        assertThatThrownBy(() -> service.outForDelivery(d.getOrderId(), otherCourier))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @Test
    void cancellationAfterPickupIsIgnoredNotFailed() {
        Delivery d = Delivery.open(UUID.randomUUID(), "PX-1", null, "Casablanca", "CASA", BigDecimal.ONE, 3, Instant.now());
        d.assign(YOUSSEF.getId(), Instant.now());
        d.startRound(Instant.now());
        when(guard.firstDelivery(any(), anyString())).thenReturn(true);
        when(deliveries.findByOrderId(d.getOrderId())).thenReturn(Optional.of(d));

        service.onOrderCancelled(UUID.randomUUID(), new OrderCancelledV1(d.getOrderId(), "PX-1", "late", Instant.now()));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        verifyNoInteractions(outbox);
    }

    @Test
    void lastFailurePublishesFailedThenReturned() {
        Delivery d = Delivery.open(UUID.randomUUID(), "PX-1", null, "Casablanca", "CASA", BigDecimal.ONE, 1, Instant.now());
        d.assign(YOUSSEF.getId(), Instant.now());
        d.startRound(Instant.now());
        when(deliveries.findByOrderId(d.getOrderId())).thenReturn(Optional.of(d));
        var ops = new AuthenticatedUser("ops", Set.of("OPS"), null, null);

        service.failed(d.getOrderId(), "refused", ops);

        var captor = ArgumentCaptor.forClass(DeliveryStatusChangedV1.class);
        verify(outbox, org.mockito.Mockito.times(2)).append(eq("Delivery"), eq(Topics.DELIVERY_STATUS), captor.capture());
        assertThat(captor.getAllValues()).extracting(DeliveryStatusChangedV1::newStatus)
                .containsExactly(DeliveryStatusV1.FAILED, DeliveryStatusV1.RETURNED);
    }

    private static DeliveryRepository.CourierLoad load(UUID courierId, long n) {
        return new DeliveryRepository.CourierLoad() {
            public UUID getCourierId() {
                return courierId;
            }

            public long getLoad() {
                return n;
            }
        };
    }

    @Test
    void courierBoardCombinesActiveLoadAndParcelsOnTheRoad() {
        var hamza = new Courier(UUID.randomUUID(), "Hamza", "+212661000002", "CASA", true);
        var omar = new Courier(UUID.randomUUID(), "Omar", "+212661000006", "KENITRA", false);
        when(couriers.findAll()).thenReturn(List.of(YOUSSEF, hamza, omar));
        when(deliveries.countLoad(any(), eq(DeliveryStatus.active())))
                .thenReturn(List.of(load(YOUSSEF.getId(), 1), load(hamza.getId(), 3)));
        when(deliveries.countLoad(any(), eq(java.util.EnumSet.of(DeliveryStatus.OUT_FOR_DELIVERY))))
                .thenReturn(List.of(load(hamza.getId(), 2)));

        List<CourierView> board = service.couriers();

        assertThat(board).extracting(CourierView::name).containsExactly("Hamza", "Youssef", "Omar");
        assertThat(board).extracting(CourierView::availability).containsExactly(CourierView.Availability.ON_ROUTE,
                CourierView.Availability.AT_HUB, CourierView.Availability.OFFLINE);
        assertThat(board.get(0).outForDelivery()).isEqualTo(2);
    }

    @Test
    void courierListAlwaysScopesToTheirOwnActiveDeliveries() {
        UUID me = UUID.randomUUID();
        var courier = new AuthenticatedUser("courier.casa1", Set.of("COURIER"), null, me);

        service.list(courier, List.of(DeliveryStatus.DELIVERED), 200);

        verify(deliveries).findByCourierIdAndStatusInOrderByUpdatedAtDesc(me, DeliveryStatus.active());
        verify(deliveries, never()).findByStatusInOrderByUpdatedAtDesc(any(), any());
    }
}
