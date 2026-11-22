package com.msetransport.parcel.order.web;

import com.msetransport.parcel.order.domain.Order;
import com.msetransport.parcel.order.domain.OrderStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Pattern(regexp = "^\\+212[5-7]\\d{8}$", message = "must be a Moroccan number like +212612345678")
            String recipientPhone,
            @NotBlank @Size(max = 255) String addressLine,
            @NotBlank @Size(max = 64) String city,
            @NotNull @DecimalMin("0.00") @DecimalMax("50000.00") @Digits(integer = 6, fraction = 2)
            BigDecimal codAmount,
            /** Only OPS may create on behalf of a merchant; ignored for MERCHANT users. */
            @Size(max = 32) String merchantId) {
    }

    public record CancelOrderRequest(@NotBlank @Size(max = 255) String reason) {
    }

    public record OrderResponse(UUID id, String trackingCode, String merchantId, String recipientName,
                                String recipientPhone, String addressLine, String city, BigDecimal codAmount,
                                OrderStatus status, int deliveryAttempts, String cancelReason, Instant createdAt,
                                Instant updatedAt) {

        static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getTrackingCode(), o.getMerchantId(), o.getRecipientName(),
                    o.getRecipientPhone(), o.getAddressLine(), o.getCity(), o.getCodAmount(), o.getStatus(),
                    o.getDeliveryAttempts(), o.getCancelReason(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        static PageResponse<OrderResponse> from(Page<Order> page) {
            return new PageResponse<>(page.map(OrderResponse::from).getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
