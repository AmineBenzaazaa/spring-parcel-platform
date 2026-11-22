package com.benzaazaa.parcel.order.domain;

import com.benzaazaa.parcel.events.delivery.DeliveryStatusV1;
import com.benzaazaa.parcel.support.web.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private static Order newOrder() {
        return Order.create("M-1001", "Salma", "+212612345678", "12 Rue Atlas", "Casablanca",
                new BigDecimal("249.9"), null, NOW);
    }

    @Test
    void newOrderIsCreatedWithTrackingCodeAndNormalisedAmount() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTrackingCode()).matches("PX-[2-9A-HJ-NP-Z]{8}");
        assertThat(order.getCodAmount()).isEqualByComparingTo("249.90");
        assertThat(order.getCodAmount().scale()).isEqualTo(2);
    }

    @Test
    void negativeCodAmountIsRejected() {
        assertThatThrownBy(() -> Order.create("M", "n", "p", "a", "c", new BigDecimal("-1"), null, NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void canBeCancelledWhileStillInTheWarehouse() {
        Order order = newOrder();
        order.applyDeliveryStatus(DeliveryStatusV1.ASSIGNED, 1, 0, NOW);

        order.cancel("customer changed mind", NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("customer changed mind");
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatusV1.class, names = {"OUT_FOR_DELIVERY", "DELIVERED", "RETURNED"})
    void cannotBeCancelledOnceTheCourierHasIt(DeliveryStatusV1 deliveryStatus) {
        Order order = newOrder();
        order.applyDeliveryStatus(deliveryStatus, 1, 1, NOW);

        assertThatThrownBy(() -> order.cancel("too late", NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can no longer be cancelled");
    }

    @ParameterizedTest
    @CsvSource({
            "ASSIGNED, ASSIGNED",
            "OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
            "FAILED, DELIVERY_FAILED",
            "DELIVERED, DELIVERED",
            "RETURNED, RETURNED",
            "CANCELLED, CANCELLED"})
    void projectsDeliveryStatusToOrderStatus(DeliveryStatusV1 deliveryStatus, OrderStatus expected) {
        Order order = newOrder();

        assertThat(order.applyDeliveryStatus(deliveryStatus, 1, 1, NOW)).isTrue();
        assertThat(order.getStatus()).isEqualTo(expected);
    }

    @Test
    void staleOrDuplicateSequenceIsIgnored() {
        Order order = newOrder();
        order.applyDeliveryStatus(DeliveryStatusV1.OUT_FOR_DELIVERY, 2, 1, NOW);

        boolean changed = order.applyDeliveryStatus(DeliveryStatusV1.ASSIGNED, 1, 0, NOW);

        assertThat(changed).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(order.getLastDeliverySequence()).isEqualTo(2);
    }

    @Test
    void unknownAndPendingStatusesDoNotChangeTheOrder() {
        Order order = newOrder();

        assertThat(order.applyDeliveryStatus(DeliveryStatusV1.UNKNOWN, 1, 0, NOW)).isFalse();
        assertThat(order.applyDeliveryStatus(DeliveryStatusV1.PENDING_ASSIGNMENT, 2, 0, NOW)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void cancelledOrderStillAcceptsTerminalOutcomeFromTheField() {
        Order order = newOrder();
        order.cancel("merchant request", NOW);

        assertThat(order.applyDeliveryStatus(DeliveryStatusV1.ASSIGNED, 1, 0, NOW)).isFalse();
        assertThat(order.applyDeliveryStatus(DeliveryStatusV1.RETURNED, 2, 1, NOW)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNED);
    }
}
