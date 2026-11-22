package com.msetransport.parcel.notification.messaging;

import com.msetransport.parcel.events.Topics;
import com.msetransport.parcel.support.messaging.KafkaErrorHandling;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

@Configuration
public class KafkaConfig {

    static final String GROUP = "notification-service";

    @Bean
    NewTopic deliveryStatusTopic() {
        return TopicBuilder.name(Topics.DELIVERY_STATUS).partitions(3).replicas(1).build();
    }

    /** Read by the live activity stream (own consumer group); created here too so startup order does not matter. */
    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deliveryStatusDlt() {
        return TopicBuilder.name(KafkaErrorHandling.dltTopic(Topics.DELIVERY_STATUS, GROUP)).partitions(1).replicas(1)
                .build();
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return KafkaErrorHandling.blockingRetryThenDlt(template, GROUP, 3, 200);
    }
}
