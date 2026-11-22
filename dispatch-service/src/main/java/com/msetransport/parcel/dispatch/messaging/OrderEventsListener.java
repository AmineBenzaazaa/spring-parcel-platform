package com.msetransport.parcel.dispatch.messaging;

import com.msetransport.parcel.dispatch.application.DispatchService;
import com.msetransport.parcel.events.EventCodec;
import com.msetransport.parcel.events.EventEnvelope;
import com.msetransport.parcel.events.EventTypes;
import com.msetransport.parcel.events.InvalidEventException;
import com.msetransport.parcel.events.Topics;
import com.msetransport.parcel.events.order.OrderCancelledV1;
import com.msetransport.parcel.events.order.OrderCreatedV1;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Non-blocking retries: a failing record is moved to {@code parcel.orders.v1-retry} and retried
 * there after a delay, so it does not block the partition for the next orders. After the last
 * attempt it lands in {@code parcel.orders.v1-dlt}. Poison messages skip retries entirely.
 *
 * <p>Trade-off vs blocking retries (order-service): throughput over strict ordering. Safe here
 * because each order has a single OrderCreated and the handler is idempotent.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final EventCodec codec;
    private final DispatchService dispatch;
    private final MeterRegistry meters;

    public OrderEventsListener(EventCodec codec, DispatchService dispatch, MeterRegistry meters) {
        this.codec = codec;
        this.dispatch = dispatch;
        this.meters = meters;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            exclude = InvalidEventException.class,
            numPartitions = "3",
            replicationFactor = "1")
    @KafkaListener(id = "dispatch-order-events", topics = Topics.ORDER_EVENTS, groupId = "dispatch-service",
            concurrency = "3")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = codec.decode(record.value());
        switch (envelope.eventType()) {
            case EventTypes.ORDER_CREATED -> dispatch.onOrderCreated(envelope.eventId(),
                    codec.payload(envelope, OrderCreatedV1.class, EventTypes.ORDER_CREATED, OrderCreatedV1.VERSION));
            case EventTypes.ORDER_CANCELLED -> dispatch.onOrderCancelled(envelope.eventId(),
                    codec.payload(envelope, OrderCancelledV1.class, EventTypes.ORDER_CANCELLED, OrderCancelledV1.VERSION));
            // tolerant reader: new event types on the topic must not break this consumer
            default -> log.debug("Ignoring event type {}", envelope.eventType());
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        meters.counter("parcel.dispatch.dead-letters").increment();
        log.error("Order event moved to DLT topic={} key={} offset={} error={}", record.topic(), record.key(),
                record.offset(), error);
    }
}
