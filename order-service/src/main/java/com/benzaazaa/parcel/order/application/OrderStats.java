package com.benzaazaa.parcel.order.application;

import com.benzaazaa.parcel.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ops KPIs for "today" (midnight in the business time zone, Africa/Casablanca).
 *
 * @param deliveredRate delivered / (delivered + returned) among parcels closed today, in percent;
 *                      null when nothing was closed yet (the UI shows a dash instead of 0%)
 */
public record OrderStats(Instant since, long ordersToday, long deliveredToday, BigDecimal codCollectedToday,
                         long inTransit, long failedOrReturnedToday, BigDecimal deliveredRate,
                         Map<OrderStatus, Long> byStatus) {

    public static OrderStats from(Instant since, Iterable<StatusRow> rows) {
        long ordersToday = 0;
        long deliveredToday = 0;
        long returnedToday = 0;
        long failedToday = 0;
        long inTransit = 0;
        BigDecimal cod = BigDecimal.ZERO;
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            byStatus.put(s, 0L);
        }
        for (StatusRow row : rows) {
            ordersToday += row.createdSince();
            byStatus.put(row.status(), row.total());
            switch (row.status()) {
                case DELIVERED -> {
                    deliveredToday = row.changedSince();
                    cod = row.codChangedSince();
                }
                case RETURNED -> returnedToday = row.changedSince();
                case DELIVERY_FAILED -> {
                    failedToday = row.changedSince();
                    inTransit += row.total();
                }
                case ASSIGNED, OUT_FOR_DELIVERY -> inTransit += row.total();
                default -> { }
            }
        }
        long closed = deliveredToday + returnedToday;
        BigDecimal rate = closed == 0 ? null
                : BigDecimal.valueOf(deliveredToday * 100).divide(BigDecimal.valueOf(closed), 1, RoundingMode.HALF_UP);
        return new OrderStats(since, ordersToday, deliveredToday, cod.setScale(2, RoundingMode.HALF_UP), inTransit,
                failedToday + returnedToday, rate, byStatus);
    }

    public record StatusRow(OrderStatus status, long total, long createdSince, long changedSince,
                            BigDecimal codChangedSince) {
    }
}
