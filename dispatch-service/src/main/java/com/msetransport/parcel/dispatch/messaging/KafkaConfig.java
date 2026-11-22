package com.msetransport.parcel.dispatch.messaging;

import com.msetransport.parcel.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Retry/DLT topics for order events are created by @RetryableTopic itself. */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deliveryStatusTopic() {
        return TopicBuilder.name(Topics.DELIVERY_STATUS).partitions(3).replicas(1).build();
    }
}
