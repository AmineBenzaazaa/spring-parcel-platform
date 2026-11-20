package com.benzaazaa.parcel.dispatch.integration;

import com.benzaazaa.parcel.dispatch.application.DispatchService;
import com.benzaazaa.parcel.dispatch.domain.Delivery;
import com.benzaazaa.parcel.dispatch.domain.DeliveryRepository;
import com.benzaazaa.parcel.dispatch.domain.DeliveryStatus;
import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.EventHeaders;
import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusV1;
import com.benzaazaa.parcel.events.order.OrderCancelledV1;
import com.benzaazaa.parcel.events.order.OrderCreatedV1;
import com.benzaazaa.parcel.support.messaging.OutboxRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class DispatchKafkaIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /** Orders whose first processing attempt should blow up (simulates a transient DB error). */
    static final Set<UUID> FAIL_ONCE = ConcurrentHashMap.newKeySet();

    @SpyBean
    DispatchService dispatch;
    @Autowired
    DeliveryRepository deliveries;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventCodec codec;

    private static OrderCreatedV1 order(String city) {
        return new OrderCreatedV1(UUID.randomUUID(), "PX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "M-1001", "Salma", "+212612345678", "12 Rue Atlas", city, new BigDecimal("320.00"), Instant.now());
    }

    /** Mirrors what order-service's outbox relay produces: key = orderId, correlation id header. */
    private void send(EventEnvelope envelope) {
        var record = new ProducerRecord<>(Topics.ORDER_EVENTS, envelope.aggregateId(), codec.encode(envelope));
        record.headers().add(EventHeaders.CORRELATION_ID, envelope.correlationId().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).join();
    }

    private Delivery awaitDelivery(UUID orderId, DeliveryStatus status) {
        return await().atMost(Duration.ofSeconds(30)).until(
                () -> deliveries.findByOrderId(orderId).filter(d -> d.getStatus() == status).orElse(null),
                d -> d != null);
    }

    private static Optional<ConsumerRecord<String, String>> pollFor(String topic, Predicate<ConsumerRecord<String, String>> match) {
        return pollFor(topic, match, 30_000);
    }

    private static Optional<ConsumerRecord<String, String>> pollFor(String topic, Predicate<ConsumerRecord<String, String>> match,
                                                                   long timeoutMs) {
        try (var consumer = new KafkaConsumer<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        return Optional.of(record);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Test
    void orderCreatedIsAssignedInZoneAndStatusEventPublished() {
        OrderCreatedV1 order = order("Rabat");

        send(codec.wrap(order, "it-dispatch-1"));

        Delivery delivery = awaitDelivery(order.orderId(), DeliveryStatus.ASSIGNED);
        assertThat(delivery.getZone()).isEqualTo("RABAT");
        assertThat(delivery.getCourierId()).isEqualTo(UUID.fromString("c0000000-0000-0000-0000-000000000003"));

        var record = pollFor(Topics.DELIVERY_STATUS, r -> order.orderId().toString().equals(r.key()));
        assertThat(record).isPresent();
        var event = codec.payload(codec.decode(record.get().value()), DeliveryStatusChangedV1.class,
                EventTypes.DELIVERY_STATUS_CHANGED, 1);
        assertThat(event.newStatus()).isEqualTo(DeliveryStatusV1.ASSIGNED);
        assertThat(new String(record.get().headers().lastHeader("x-correlation-id").value(), StandardCharsets.UTF_8))
                .as("correlation id survives Kafka -> service -> outbox -> Kafka").isEqualTo("it-dispatch-1");
    }

    @Test
    void sameEventDeliveredTwiceCreatesOneDeliveryAndOneStatusEvent() {
        OrderCreatedV1 order = order("Casablanca");
        EventEnvelope envelope = codec.wrap(order, "it-dup");

        send(envelope);
        send(envelope);
        // a later event on the same key proves both copies were consumed before we assert
        send(codec.wrap(new OrderCancelledV1(order.orderId(), order.trackingCode(), "test", Instant.now()), "it-dup"));

        awaitDelivery(order.orderId(), DeliveryStatus.CANCELLED);
        assertThat(outbox.findByAggregateIdOrderBySeq(order.orderId().toString()))
                .extracting(r -> codec.payload(codec.decode(r.getPayload()), DeliveryStatusChangedV1.class,
                        EventTypes.DELIVERY_STATUS_CHANGED, 1).newStatus())
                .containsExactly(DeliveryStatusV1.ASSIGNED, DeliveryStatusV1.CANCELLED);
    }

    @Test
    void poisonMessageSkipsRetriesAndLandsInDlt() {
        String key = "poison-" + UUID.randomUUID();
        kafka.send(Topics.ORDER_EVENTS, key, "{\"eventType\":\"OrderCreated\"}").join();

        var dead = pollFor(Topics.ORDER_EVENTS + "-dlt", r -> key.equals(r.key()));

        assertThat(dead).isPresent();
        assertThat(pollFor(Topics.ORDER_EVENTS + "-retry", r -> key.equals(r.key()), 5_000))
                .as("non-retryable: never visits the retry topic").isEmpty();
    }

    @Test
    void transientFailureIsRetriedThroughTheRetryTopic() {
        OrderCreatedV1 order = order("Marrakech");
        FAIL_ONCE.add(order.orderId());
        doAnswer(inv -> {
            OrderCreatedV1 event = inv.getArgument(1);
            if (FAIL_ONCE.remove(event.orderId())) {
                throw new IllegalStateException("simulated DB blip");
            }
            return inv.callRealMethod();
        }).when(dispatch).onOrderCreated(any(), any());

        send(codec.wrap(order, "it-retry"));

        awaitDelivery(order.orderId(), DeliveryStatus.ASSIGNED);
        assertThat(pollFor(Topics.ORDER_EVENTS + "-retry", r -> order.orderId().toString().equals(r.key())))
                .isPresent();
    }
}
