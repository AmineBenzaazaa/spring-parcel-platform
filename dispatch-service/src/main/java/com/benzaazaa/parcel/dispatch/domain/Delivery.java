package com.benzaazaa.parcel.dispatch.domain;

import com.benzaazaa.parcel.support.web.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Delivery aggregate: owns the state machine and attempt counting. */
@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    private UUID id;
    private UUID orderId;
    private String trackingCode;
    private String recipientPhone;
    private String city;
    private String zone;
    private BigDecimal codAmount;
    private UUID courierId;
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;
    private int attempts;
    private int maxAttempts;
    private String lastFailureReason;
    /** Incremented on every change and copied into events so consumers can drop stale ones. */
    private long sequence;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    protected Delivery() {
    }

    public static Delivery open(UUID orderId, String trackingCode, String recipientPhone, String city, String zone,
                                BigDecimal codAmount, int maxAttempts, Instant now) {
        Delivery d = new Delivery();
        d.id = UUID.randomUUID();
        d.orderId = orderId;
        d.trackingCode = trackingCode;
        d.recipientPhone = recipientPhone;
        d.city = city;
        d.zone = zone;
        d.codAmount = codAmount;
        d.status = DeliveryStatus.PENDING_ASSIGNMENT;
        d.maxAttempts = maxAttempts;
        d.createdAt = now;
        d.updatedAt = now;
        return d;
    }

    /** The initial "waiting for a courier" state, published so ops dashboards see unserved parcels. */
    public StatusChange pending() {
        sequence++;
        return new StatusChange(null, DeliveryStatus.PENDING_ASSIGNMENT, attempts, "no courier available in zone",
                sequence);
    }

    public StatusChange assign(UUID courierId, Instant now) {
        StatusChange change = transition(DeliveryStatus.ASSIGNED, null, now);
        this.courierId = courierId;
        return change;
    }

    public StatusChange startRound(Instant now) {
        if (status == DeliveryStatus.FAILED && attempts >= maxAttempts) {
            throw new BusinessRuleException("max-attempts-reached", "No delivery attempts left for " + trackingCode);
        }
        attempts++;
        return transition(DeliveryStatus.OUT_FOR_DELIVERY, null, now);
    }

    public StatusChange markDelivered(Instant now) {
        return transition(DeliveryStatus.DELIVERED, null, now);
    }

    /**
     * A failed attempt. After the last allowed attempt the parcel is automatically returned to the
     * merchant: that compensating step is the end of the saga for an undeliverable COD parcel.
     */
    public List<StatusChange> markFailed(String reason, Instant now) {
        List<StatusChange> changes = new ArrayList<>();
        changes.add(transition(DeliveryStatus.FAILED, reason, now));
        this.lastFailureReason = reason;
        if (attempts >= maxAttempts) {
            changes.add(transition(DeliveryStatus.RETURNED, "max attempts reached: " + reason, now));
        }
        return changes;
    }

    public boolean isCancellable() {
        return status.canTransitionTo(DeliveryStatus.CANCELLED);
    }

    public StatusChange cancel(String reason, Instant now) {
        return transition(DeliveryStatus.CANCELLED, reason, now);
    }

    private StatusChange transition(DeliveryStatus next, String reason, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException("illegal-transition",
                    "Delivery " + trackingCode + " cannot go from " + status + " to " + next);
        }
        DeliveryStatus previous = status;
        status = next;
        sequence++;
        updatedAt = now;
        return new StatusChange(previous, next, attempts, reason, sequence);
    }

    public boolean isAssignedTo(UUID courier) {
        return courierId != null && courierId.equals(courier);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getCity() {
        return city;
    }

    public String getZone() {
        return zone;
    }

    public BigDecimal getCodAmount() {
        return codAmount;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public long getSequence() {
        return sequence;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
