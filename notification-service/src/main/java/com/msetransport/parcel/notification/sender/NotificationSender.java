package com.msetransport.parcel.notification.sender;

import com.msetransport.parcel.notification.domain.Channel;
import com.msetransport.parcel.notification.domain.DeliveryResult;

/**
 * Strategy interface (port). Every Spring bean implementing it is a channel; adding WhatsApp or
 * e-mail is a new bean, no change to the consumer (open/closed principle).
 */
public interface NotificationSender {

    Channel channel();

    /** Must not throw: failures are reported in the result so one channel cannot block the others. */
    DeliveryResult send(OutgoingMessage message);
}
