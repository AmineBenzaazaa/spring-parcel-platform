package com.msetransport.parcel.dispatch.domain;

import com.msetransport.parcel.support.web.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final UUID COURIER = UUID.randomUUID();

    private static Delivery assigned() {
        Delivery d = Delivery.open(UUID.randomUUID(), "PX-TEST0001", "+212612345678", "Casablanca", "CASA",
                new BigDecimal("199.00"), 3, NOW);
        d.assign(COURIER, NOW);
        return d;
    }

    @Test
    void happyPathIncrementsSequenceOnEveryChange() {
        Delivery d = assigned();

        StatusChange out = d.startRound(NOW);
        StatusChange delivered = d.markDelivered(NOW);

        assertThat(out.previous()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(out.attempt()).isEqualTo(1);
        assertThat(delivered.next()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivered.sequence()).isEqualTo(3); // assign=1, out=2, delivered=3
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void cannotDeliverWithoutGoingOutFirst() {
        Delivery d = assigned();

        assertThatThrownBy(() -> d.markDelivered(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot go from ASSIGNED to DELIVERED");
    }

    @Test
    void failedAttemptCanBeRetried() {
        Delivery d = assigned();
        d.startRound(NOW);

        List<StatusChange> changes = d.markFailed("customer absent", NOW);
        d.startRound(NOW);

        assertThat(changes).extracting(StatusChange::next).containsExactly(DeliveryStatus.FAILED);
        assertThat(d.getAttempts()).isEqualTo(2);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        assertThat(d.getLastFailureReason()).isEqualTo("customer absent");
    }

    @Test
    void lastFailedAttemptTriggersReturnToMerchant() {
        Delivery d = assigned();
        for (int i = 0; i < 2; i++) {
            d.startRound(NOW);
            d.markFailed("phone off", NOW);
        }
        d.startRound(NOW);

        List<StatusChange> changes = d.markFailed("refused parcel", NOW);

        assertThat(changes).extracting(StatusChange::next).containsExactly(DeliveryStatus.FAILED, DeliveryStatus.RETURNED);
        assertThat(changes.get(1).sequence()).isGreaterThan(changes.get(0).sequence());
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.RETURNED);
        assertThat(d.getAttempts()).isEqualTo(3);
    }

    @Test
    void cancellableOnlyBeforePickup() {
        Delivery d = assigned();
        assertThat(d.isCancellable()).isTrue();

        d.startRound(NOW);

        assertThat(d.isCancellable()).isFalse();
        assertThatThrownBy(() -> d.cancel("too late", NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pendingDeliveryHasNoCourierUntilAssigned() {
        Delivery d = Delivery.open(UUID.randomUUID(), "PX-1", null, "Agadir", null, BigDecimal.ONE, 3, NOW);

        StatusChange pending = d.pending();

        assertThat(pending.next()).isEqualTo(DeliveryStatus.PENDING_ASSIGNMENT);
        assertThat(d.getCourierId()).isNull();
        assertThat(d.isAssignedTo(COURIER)).isFalse();
    }
}
