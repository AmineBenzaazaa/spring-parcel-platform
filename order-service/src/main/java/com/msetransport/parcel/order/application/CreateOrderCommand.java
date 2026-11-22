package com.msetransport.parcel.order.application;

import java.math.BigDecimal;

public record CreateOrderCommand(String merchantId, String recipientName, String recipientPhone, String addressLine,
                                 String city, BigDecimal codAmount, String idempotencyKey) {
}
