package com.msetransport.parcel.dispatch.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    Optional<Delivery> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<Delivery> findByCourierIdAndStatusInOrderByUpdatedAtDesc(UUID courierId, Collection<DeliveryStatus> statuses);

    List<Delivery> findTop50ByOrderByUpdatedAtDesc();

    List<Delivery> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<Delivery> findByStatusInOrderByUpdatedAtDesc(Collection<DeliveryStatus> statuses, Pageable pageable);

    @Query("""
            SELECT d.courierId AS courierId, COUNT(d) AS load FROM Delivery d
            WHERE d.courierId IN :courierIds AND d.status IN :statuses
            GROUP BY d.courierId
            """)
    List<CourierLoad> countLoad(@Param("courierIds") Collection<UUID> courierIds,
                                @Param("statuses") Collection<DeliveryStatus> statuses);

    interface CourierLoad {
        UUID getCourierId();

        long getLoad();
    }
}
