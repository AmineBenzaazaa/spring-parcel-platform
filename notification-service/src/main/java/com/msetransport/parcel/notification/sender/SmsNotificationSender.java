package com.msetransport.parcel.notification.sender;

import com.msetransport.parcel.notification.domain.Channel;
import com.msetransport.parcel.notification.domain.DeliveryResult;
import com.msetransport.parcel.notification.sms.SmsGatewayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
public class SmsNotificationSender implements NotificationSender {

    private final SmsGatewayClient gateway;

    public SmsNotificationSender(SmsGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public DeliveryResult send(OutgoingMessage message) {
        if (message.recipientPhone() == null) {
            return DeliveryResult.skipped("no recipient phone");
        }
        return gateway.send(message.recipientPhone(), message.text());
    }
}
