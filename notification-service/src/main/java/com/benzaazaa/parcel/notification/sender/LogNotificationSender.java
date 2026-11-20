package com.benzaazaa.parcel.notification.sender;

import com.benzaazaa.parcel.notification.domain.Channel;
import com.benzaazaa.parcel.notification.domain.DeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default channel: writes the notification to the log. Always on, never fails. */
@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public Channel channel() {
        return Channel.LOG;
    }

    @Override
    public DeliveryResult send(OutgoingMessage message) {
        log.info("NOTIFY [LOG] order={} to={} text=\"{}\"", message.trackingCode(), mask(message.recipientPhone()),
                message.text());
        return DeliveryResult.sent("log");
    }

    /** No full phone numbers in logs (privacy): +212612345678 -> +2126*****678 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 8) {
            return "***";
        }
        return phone.substring(0, 5) + "*".repeat(phone.length() - 8) + phone.substring(phone.length() - 3);
    }
}
