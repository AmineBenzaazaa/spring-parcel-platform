package com.msetransport.parcel.order.domain;

import com.msetransport.parcel.events.delivery.DeliveryStatusV1;
import com.msetransport.parcel.support.web.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order aggregate. Business rules live here (not in the service) so they are unit-testable
 * without Spring. State changes return booleans so callers know whether to emit an event.
 */
@Entity
@Table(name = "parcel_order")
public class Order {

    @Id
    private UUID id;
    private String trackingCode;
    private String merchantId;
    private String recipientName;
    private String recipientPhone;
    private String addressLine;
    private String city;
    private BigDecimal codAmount;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private int deliveryAttempts;
    private long lastDeliverySequence;
    private String cancelReason;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;
    /** Optimistic locking: concurrent cancel + status event on the same row cannot silently overwrite. */
    @Version
    private long version;

    protected Order() {
    }

    public static Order create(String merchantId, String recipientName, String recipientPhone, String addressLine,
                               String city, BigDecimal codAmount, String idempotencyKey, Instant now) {
        if (codAmount == null || codAmount.signum() < 0) {
            throw new BusinessRuleException("invalid-cod-amount", "COD amount must be zero or positive");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.trackingCode = TrackingCodes.next();
        order.merchantId = merchantId;
        order.recipientName = recipientName;
        order.recipientPhone = recipientPhone;
        order.addressLine = addressLine;
        order.city = city;
        order.codAmount = codAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        order.status = OrderStatus.CREATED;
        order.idempotencyKey = idempotencyKey;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public void cancel(String reason, Instant now) {
        if (!status.isCancellable()) {
            throw new BusinessRuleException("order-not-cancellable",
                    "Order " + trackingCode + " is " + status + " and can no longer be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = now;
    }

    /**
     * Projects a delivery status coming from dispatch-service (eventual consistency).
     *
     * @return true if the order changed; false for stale/out-of-order or irrelevant events
     */
    public boolean applyDeliveryStatus(DeliveryStatusV1 deliveryStatus, long sequence, int attempt, Instant now) {
        if (sequence <= lastDeliverySequence) {
            return false; // older than what we already applied (retry topic / redelivery reordering)
        }
        this.lastDeliverySequence = sequence;
        OrderStatus target = map(deliveryStatus);
        if (target == null) {
            return false;
        }
        // A cancelled order only accepts a terminal outcome from the field (the courier already left).
        if (status == OrderStatus.CANCELLED && target != OrderStatus.DELIVERED && target != OrderStatus.RETURNED) {
            return false;
        }
        this.status = target;
        this.deliveryAttempts = Math.max(deliveryAttempts, attempt);
        this.updatedAt = now;
        return true;
    }

    static OrderStatus map(DeliveryStatusV1 deliveryStatus) {
        return switch (deliveryStatus) {
            case ASSIGNED -> OrderStatus.ASSIGNED;
            case OUT_FOR_DELIVERY -> OrderStatus.OUT_FOR_DELIVERY;
            case FAILED -> OrderStatus.DELIVERY_FAILED;
            case DELIVERED -> OrderStatus.DELIVERED;
            case RETURNED -> OrderStatus.RETURNED;
            case CANCELLED -> OrderStatus.CANCELLED;
            case PENDING_ASSIGNMENT, UNKNOWN -> null;
        };
    }

    public boolean isOwnedBy(String merchantId) {
        return this.merchantId.equals(merchantId);
    }

    public UUID getId() {
        return id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public BigDecimal getCodAmount() {
        return codAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public long getLastDeliverySequence() {
        return lastDeliverySequence;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
