package com.benzaazaa.parcel.notification.sender;

import java.util.UUID;

public record OutgoingMessage(UUID orderId, String trackingCode, String recipientPhone, String text) {
}
