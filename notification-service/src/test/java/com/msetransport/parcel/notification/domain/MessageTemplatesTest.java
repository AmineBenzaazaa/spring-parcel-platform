package com.msetransport.parcel.notification.domain;

import com.msetransport.parcel.events.delivery.DeliveryStatusChangedV1;
import com.msetransport.parcel.events.delivery.DeliveryStatusV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplatesTest {

    private static DeliveryStatusChangedV1 event(DeliveryStatusV1 status, String reason) {
        return new DeliveryStatusChangedV1(UUID.randomUUID(), UUID.randomUUID(), "PX-7KQ2M9XA", null, null, status,
                2, reason, "+212612345678", new BigDecimal("249.9"), 1, Instant.now());
    }

    @Test
    void outForDeliveryTellsTheCustomerHowMuchCashToPrepare() {
        assertThat(MessageTemplates.render(event(DeliveryStatusV1.OUT_FOR_DELIVERY, null)))
                .hasValue("Your parcel PX-7KQ2M9XA is out for delivery today. Please prepare 249.90 MAD in cash.");
    }

    @Test
    void failedAttemptIncludesAttemptNumberAndReason() {
        assertThat(MessageTemplates.render(event(DeliveryStatusV1.FAILED, "customer absent")).orElseThrow())
                .contains("attempt 2").contains("customer absent");
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatusV1.class, names = {"ASSIGNED", "DELIVERED", "RETURNED", "CANCELLED"})
    void customerFacingStatusesProduceAMessage(DeliveryStatusV1 status) {
        assertThat(MessageTemplates.render(event(status, null))).isPresent().get().asString().contains("PX-7KQ2M9XA");
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatusV1.class, names = {"PENDING_ASSIGNMENT", "UNKNOWN"})
    void internalStatusesProduceNoMessage(DeliveryStatusV1 status) {
        assertThat(MessageTemplates.render(event(status, null))).isEmpty();
    }
}
