package com.msetransport.parcel.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    Page<Order> findByMerchantId(String merchantId, Pageable pageable);

    Page<Order> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);

    Page<Order> findByMerchantIdAndStatusIn(String merchantId, Collection<OrderStatus> statuses, Pageable pageable);

    /**
     * One pass over the table for the ops KPIs: per status, all-time count, created since, changed
     * since and COD amount of rows changed since. {@code merchantId = ''} means all merchants.
     * Rows: [status, total, createdSince, changedSince, codChangedSince].
     */
    @Query(nativeQuery = true, value = """
            SELECT status,
                   COUNT(*)                                                        AS total,
                   COUNT(*) FILTER (WHERE created_at >= :since)                    AS created_since,
                   COUNT(*) FILTER (WHERE updated_at >= :since)                    AS changed_since,
                   COALESCE(SUM(cod_amount) FILTER (WHERE updated_at >= :since), 0) AS cod_changed_since
            FROM parcel_order
            WHERE (:merchantId = '' OR merchant_id = :merchantId)
            GROUP BY status
            """)
    List<Object[]> statusStats(@Param("since") Instant since, @Param("merchantId") String merchantId);
}
