package com.msetransport.parcel.notification.web;

import com.msetransport.parcel.notification.application.NotificationService;
import com.msetransport.parcel.notification.domain.Channel;
import com.msetransport.parcel.notification.domain.DeliveryResult;
import com.msetransport.parcel.notification.domain.Notification;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('OPS')")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "Ops: notifications sent for an order (or the latest 50)")
    @GetMapping("/api/notifications")
    public List<NotificationResponse> list(@RequestParam(required = false) UUID orderId) {
        var list = orderId == null ? notifications.latest() : notifications.forOrder(orderId);
        return list.stream().map(NotificationResponse::from).toList();
    }

    public record NotificationResponse(UUID id, UUID orderId, String trackingCode, Channel channel, String message,
                                       DeliveryResult.Status status, String error, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getOrderId(), n.getTrackingCode(), n.getChannel(),
                    n.getMessage(), n.getStatus(), n.getError(), n.getCreatedAt());
        }
    }
}
