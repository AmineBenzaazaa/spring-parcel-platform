package com.msetransport.parcel.order.application;

import com.msetransport.parcel.order.application.OrderStats.StatusRow;
import com.msetransport.parcel.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatsTest {

    private static final Instant MIDNIGHT = Instant.parse("2026-11-19T23:00:00Z");

    @Test
    void kpisCombineTodayCountsWithTheLiveInTransitSnapshot() {
        var stats = OrderStats.from(MIDNIGHT, List.of(
                new StatusRow(OrderStatus.CREATED, 1, 1, 1, new BigDecimal("80.00")),
                new StatusRow(OrderStatus.ASSIGNED, 3, 2, 2, BigDecimal.ZERO),
                new StatusRow(OrderStatus.OUT_FOR_DELIVERY, 2, 1, 2, BigDecimal.ZERO),
                new StatusRow(OrderStatus.DELIVERY_FAILED, 1, 0, 1, BigDecimal.ZERO),
                new StatusRow(OrderStatus.DELIVERED, 9, 3, 4, new BigDecimal("999.60")),
                new StatusRow(OrderStatus.RETURNED, 2, 0, 1, new BigDecimal("120.00"))));

        assertThat(stats.ordersToday()).isEqualTo(7);
        assertThat(stats.deliveredToday()).isEqualTo(4);
        assertThat(stats.codCollectedToday()).isEqualByComparingTo("999.60");   // only DELIVERED cash counts
        assertThat(stats.inTransit()).isEqualTo(6);                             // assigned + out + failed (all time)
        assertThat(stats.failedOrReturnedToday()).isEqualTo(2);
        assertThat(stats.deliveredRate()).isEqualByComparingTo("80.0");         // 4 / (4 + 1)
        assertThat(stats.byStatus()).containsEntry(OrderStatus.CANCELLED, 0L).containsEntry(OrderStatus.DELIVERED, 9L);
    }

    @Test
    void deliveredRateIsNullBeforeAnyParcelIsClosed() {
        var stats = OrderStats.from(MIDNIGHT, List.of(new StatusRow(OrderStatus.CREATED, 2, 2, 2, BigDecimal.ZERO)));

        assertThat(stats.deliveredRate()).isNull();
        assertThat(stats.codCollectedToday()).isEqualByComparingTo("0.00");
    }
}
