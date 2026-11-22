package com.msetransport.parcel.support.messaging;

import com.msetransport.parcel.events.EventCodec;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class MessagingConfig {

    @Bean
    EventCodec eventCodec(Clock clock) {
        return new EventCodec(clock);
    }

    @Bean
    KafkaCorrelationInterceptor kafkaCorrelationInterceptor() {
        return new KafkaCorrelationInterceptor();
    }

    @Bean
    @ConditionalOnProperty(name = "parcel.outbox.enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, Clock clock,
                            MeterRegistry meters,
                            @Value("${parcel.outbox.relay.batch-size:100}") int batchSize,
                            @Value("${parcel.outbox.relay.send-timeout:10s}") Duration sendTimeout) {
        return new OutboxRelay(repository, kafka, clock, meters, batchSize, sendTimeout);
    }

    /** Disabled in some tests to drive the relay by hand. */
    @Bean
    @ConditionalOnExpression("${parcel.outbox.enabled:true} and ${parcel.outbox.relay.scheduled:true}")
    OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
        return new OutboxRelayScheduler(relay);
    }
}
