package com.benzaazaa.parcel.dispatch.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PENDING_ASSIGNMENT, ASSIGNED, true",
            "PENDING_ASSIGNMENT, OUT_FOR_DELIVERY, false",
            "ASSIGNED, OUT_FOR_DELIVERY, true",
            "ASSIGNED, DELIVERED, false",
            "ASSIGNED, CANCELLED, true",
            "OUT_FOR_DELIVERY, DELIVERED, true",
            "OUT_FOR_DELIVERY, FAILED, true",
            "OUT_FOR_DELIVERY, CANCELLED, false",
            "FAILED, OUT_FOR_DELIVERY, true",
            "FAILED, RETURNED, true",
            "FAILED, DELIVERED, false",
            "DELIVERED, RETURNED, false",
            "RETURNED, OUT_FOR_DELIVERY, false",
            "CANCELLED, ASSIGNED, false"})
    void transitionTable(DeliveryStatus from, DeliveryStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void terminalStatesAndActiveLoad() {
        assertThat(DeliveryStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(DeliveryStatus.RETURNED.isTerminal()).isTrue();
        assertThat(DeliveryStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(DeliveryStatus.FAILED.isTerminal()).isFalse();
        assertThat(DeliveryStatus.active()).containsExactlyInAnyOrder(DeliveryStatus.ASSIGNED,
                DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.FAILED);
    }
}
