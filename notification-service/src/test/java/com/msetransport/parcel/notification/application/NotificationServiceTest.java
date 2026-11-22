package com.benzaazaa.parcel.notification.application;

import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusV1;
import com.benzaazaa.parcel.notification.domain.Channel;
import com.benzaazaa.parcel.notification.domain.DeliveryResult;
import com.benzaazaa.parcel.notification.domain.Notification;
import com.benzaazaa.parcel.notification.domain.NotificationRepository;
import com.benzaazaa.parcel.notification.sender.NotificationSender;
import com.benzaazaa.parcel.notification.sender.OutgoingMessage;
import com.benzaazaa.parcel.support.messaging.IdempotencyGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    private final NotificationSender log = sender(Channel.LOG, DeliveryResult.sent("log"));
    private final NotificationSender sms = sender(Channel.SMS, DeliveryResult.skipped("circuit open"));
    private final NotificationService service = new NotificationService(List.of(log, sms), repository, guard,
            Clock.systemUTC(), new SimpleMeterRegistry());

    private static NotificationSender sender(Channel channel, DeliveryResult result) {
        NotificationSender s = mock(NotificationSender.class);
        when(s.channel()).thenReturn(channel);
        when(s.send(any(OutgoingMessage.class))).thenReturn(result);
        return s;
    }

    private static DeliveryStatusChangedV1 event(DeliveryStatusV1 status) {
        return new DeliveryStatusChangedV1(UUID.randomUUID(), UUID.randomUUID(), "PX-1", null, null, status, 1, null,
                "+212612345678", BigDecimal.TEN, 1, Instant.now());
    }

    @Test
    void sendsOnEveryChannelAndRecordsEachOutcome() {
        when(guard.firstDelivery(any(), anyString())).thenReturn(true);

        service.handle(UUID.randomUUID(), event(DeliveryStatusV1.OUT_FOR_DELIVERY));

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getChannel, Notification::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Channel.LOG, DeliveryResult.Status.SENT),
                        org.assertj.core.groups.Tuple.tuple(Channel.SMS, DeliveryResult.Status.SKIPPED));
    }

    @Test
    void duplicateEventSendsNothing() {
        when(guard.firstDelivery(any(), anyString())).thenReturn(false);

        service.handle(UUID.randomUUID(), event(DeliveryStatusV1.DELIVERED));

        verify(log, never()).send(any());
        verify(repository, never()).save(any());
    }

    @Test
    void internalStatusSendsNothing() {
        when(guard.firstDelivery(any(), anyString())).thenReturn(true);

        service.handle(UUID.randomUUID(), event(DeliveryStatusV1.PENDING_ASSIGNMENT));

        verify(log, never()).send(any());
    }

    @Test
    void phoneNumbersAreMaskedInLogs() {
        assertThat(com.benzaazaa.parcel.notification.sender.LogNotificationSender.mask("+212612345678"))
                .isEqualTo("+2126*****678");
    }
}
