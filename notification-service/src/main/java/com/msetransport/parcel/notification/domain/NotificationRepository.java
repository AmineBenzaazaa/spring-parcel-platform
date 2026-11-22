package com.benzaazaa.parcel.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByOrderIdOrderByCreatedAt(UUID orderId);

    List<Notification> findByEventId(UUID eventId);

    List<Notification> findTop50ByOrderByCreatedAtDesc();
}
