package com.benzaazaa.parcel.order.messaging;

import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.support.messaging.KafkaErrorHandling;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

@Configuration
public class KafkaConfig {

    static final String GROUP = "order-service";

    /** 3 partitions: up to 3 dispatch consumers in parallel, per-order ordering kept by the key. */
    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deliveryStatusTopic() {
        return TopicBuilder.name(Topics.DELIVERY_STATUS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deliveryStatusDlt() {
        return TopicBuilder.name(KafkaErrorHandling.dltTopic(Topics.DELIVERY_STATUS, GROUP)).partitions(1).replicas(1)
                .build();
    }

    /** Picked up by Spring Boot's listener container factory. 3 retries (200ms, 400ms, 800ms) then DLT. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return KafkaErrorHandling.blockingRetryThenDlt(template, GROUP, 3, 200);
    }
}
