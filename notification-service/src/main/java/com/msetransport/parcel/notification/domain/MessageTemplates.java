package com.benzaazaa.parcel.notification.domain;

import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;

import java.math.BigDecimal;
import java.util.Optional;

/** Customer-facing SMS text per delivery status. Statuses that concern only ops produce no message. */
public final class MessageTemplates {

    private MessageTemplates() {
    }

    public static Optional<String> render(DeliveryStatusChangedV1 e) {
        String code = e.trackingCode();
        String amount = formatMad(e.codAmount());
        String text = switch (e.newStatus()) {
            case ASSIGNED -> "Your parcel " + code + " has been assigned to a courier and will be delivered soon.";
            case OUT_FOR_DELIVERY -> "Your parcel " + code + " is out for delivery today. Please prepare "
                    + amount + " in cash.";
            case DELIVERED -> "Parcel " + code + " delivered and " + amount + " received. Thank you!";
            case FAILED -> "We missed you for parcel " + code + " (attempt " + e.attempt() + ")"
                    + (e.reason() == null ? "" : ": " + e.reason()) + ". We will try again.";
            case RETURNED -> "Parcel " + code + " could not be delivered and is being returned to the seller.";
            case CANCELLED -> "Your order " + code + " was cancelled by the seller.";
            case PENDING_ASSIGNMENT, UNKNOWN -> null;
        };
        return Optional.ofNullable(text);
    }

    static String formatMad(BigDecimal amount) {
        return amount == null ? "the amount due" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " MAD";
    }
}
