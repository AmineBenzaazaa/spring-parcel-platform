package com.msetransport.parcel.dispatch.web;

import com.msetransport.parcel.dispatch.application.CourierView;
import com.msetransport.parcel.dispatch.application.DispatchService;
import com.msetransport.parcel.dispatch.domain.Delivery;
import com.msetransport.parcel.dispatch.domain.DeliveryStatus;
import com.msetransport.parcel.support.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('COURIER', 'OPS')")
public class DeliveryController {

    private final DispatchService dispatch;

    public DeliveryController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Operation(summary = "Courier: my active deliveries. Ops: latest deliveries (limit up to 200, optional ?status= filter)")
    @GetMapping("/api/deliveries")
    public List<DeliveryResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "status", required = false) List<DeliveryStatus> statuses,
            @RequestParam(defaultValue = "50") int limit) {
        return dispatch.list(AuthenticatedUser.from(jwt), statuses, limit).stream().map(DeliveryResponse::from)
                .toList();
    }

    @GetMapping("/api/deliveries/{orderId}")
    public DeliveryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return DeliveryResponse.from(dispatch.get(orderId, AuthenticatedUser.from(jwt)));
    }

    @Operation(summary = "Courier picked up the parcel and left the hub")
    @PostMapping("/api/deliveries/{orderId}/out-for-delivery")
    public DeliveryResponse outForDelivery(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return DeliveryResponse.from(dispatch.outForDelivery(orderId, AuthenticatedUser.from(jwt)));
    }

    @Operation(summary = "Parcel handed over and cash collected")
    @PostMapping("/api/deliveries/{orderId}/delivered")
    public DeliveryResponse delivered(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return DeliveryResponse.from(dispatch.delivered(orderId, AuthenticatedUser.from(jwt)));
    }

    @Operation(summary = "Attempt failed (customer absent, refused, wrong address). Last attempt triggers RETURNED")
    @PostMapping("/api/deliveries/{orderId}/failed")
    public DeliveryResponse failed(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                   @Valid @RequestBody FailRequest request) {
        return DeliveryResponse.from(dispatch.failed(orderId, request.reason(), AuthenticatedUser.from(jwt)));
    }

    @Operation(summary = "Ops: manually assign a pending delivery")
    @PreAuthorize("hasRole('OPS')")
    @PostMapping("/api/deliveries/{orderId}/assign")
    public DeliveryResponse assign(@PathVariable UUID orderId, @Valid @RequestBody AssignRequest request) {
        return DeliveryResponse.from(dispatch.assign(orderId, request.courierId()));
    }

    @Operation(summary = "Ops: couriers with zone, active load and availability")
    @PreAuthorize("hasRole('OPS')")
    @GetMapping("/api/couriers")
    public List<CourierView> couriers() {
        return dispatch.couriers();
    }

    public record FailRequest(@NotBlank @Size(max = 255) String reason) {
    }

    public record AssignRequest(@NotNull UUID courierId) {
    }

    public record DeliveryResponse(UUID id, UUID orderId, String trackingCode, String city, String zone,
                                   BigDecimal codAmount, UUID courierId, DeliveryStatus status, int attempts,
                                   int maxAttempts, String lastFailureReason, Instant updatedAt) {

        static DeliveryResponse from(Delivery d) {
            return new DeliveryResponse(d.getId(), d.getOrderId(), d.getTrackingCode(), d.getCity(), d.getZone(),
                    d.getCodAmount(), d.getCourierId(), d.getStatus(), d.getAttempts(), d.getMaxAttempts(),
                    d.getLastFailureReason(), d.getUpdatedAt());
        }
    }
}
