package com.benzaazaa.parcel.order.integration;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.EventHeaders;
import com.benzaazaa.parcel.events.EventTypes;
import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusV1;
import com.benzaazaa.parcel.order.application.CreateOrderCommand;
import com.benzaazaa.parcel.order.application.DeliveryStatusProjector;
import com.benzaazaa.parcel.order.application.OrderService;
import com.benzaazaa.parcel.order.domain.Order;
import com.benzaazaa.parcel.order.domain.OrderRepository;
import com.benzaazaa.parcel.order.domain.OrderStatus;
import com.benzaazaa.parcel.support.correlation.CorrelationId;
import com.benzaazaa.parcel.support.messaging.KafkaErrorHandling;
import com.benzaazaa.parcel.support.messaging.OutboxRepository;
import com.benzaazaa.parcel.support.messaging.ProcessedEvent;
import com.benzaazaa.parcel.support.messaging.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Postgres + real Kafka (KRaft) in containers. Proves: outbox row -> relay -> Kafka record with
 * key and correlation header; idempotent projection of DeliveryStatusChanged; poison message -> DLT.
 */
@SpringBootTest
class OrderMessagingIntegrationTest {

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

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orders;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    ProcessedEventRepository processed;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventCodec codec;

    private Order createOrder() {
        return orderService.create(new CreateOrderCommand("M-1001", "Salma", "+212612345678", "12 Rue Atlas",
                "Casablanca", new BigDecimal("249.90"), null));
    }

    private static KafkaConsumer<String, String> consumer(String topic) {
        var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private static Optional<ConsumerRecord<String, String>> pollFor(String topic, java.util.function.Predicate<ConsumerRecord<String, String>> match) {
        try (var consumer = consumer(topic)) {
            long deadline = System.currentTimeMillis() + 20_000;
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

    private static String header(ConsumerRecord<String, String> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private void sendStatus(EventEnvelope envelope) {
        kafka.send(Topics.DELIVERY_STATUS, envelope.aggregateId(), codec.encode(envelope)).join();
    }

    private EventEnvelope statusEvent(Order order, DeliveryStatusV1 status, long sequence) {
        return codec.wrap(new DeliveryStatusChangedV1(UUID.randomUUID(), order.getId(), order.getTrackingCode(),
                UUID.randomUUID(), null, status, 1, null, order.getRecipientPhone(), order.getCodAmount(), sequence,
                Instant.now()), "it-status");
    }

    @Test
    void outboxRelayPublishesOrderCreatedKeyedByOrderIdWithCorrelationHeader() {
        CorrelationId.set("it-corr-1");
        Order order;
        try {
            order = createOrder();
        } finally {
            CorrelationId.clear();
        }
        var row = outbox.findByAggregateIdOrderBySeq(order.getId().toString()).getFirst();

        var record = pollFor(Topics.ORDER_EVENTS, r -> order.getId().toString().equals(r.key()));

        assertThat(record).isPresent();
        assertThat(header(record.get(), EventHeaders.EVENT_TYPE)).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(header(record.get(), EventHeaders.CORRELATION_ID)).isEqualTo("it-corr-1");
        assertThat(codec.decode(record.get().value()).eventId()).isEqualTo(row.getId());
        await().atMost(Duration.ofSeconds(10))
                .until(() -> outbox.findById(row.getId()).orElseThrow().getPublishedAt() != null);
    }

    @Test
    void deliveryStatusIsProjectedAndADuplicateEventHasOneEffect() {
        Order order = createOrder();
        EventEnvelope assigned = statusEvent(order, DeliveryStatusV1.ASSIGNED, 1);

        sendStatus(assigned);
        sendStatus(assigned); // at-least-once: same eventId delivered twice
        sendStatus(statusEvent(order, DeliveryStatusV1.OUT_FOR_DELIVERY, 2));

        await().atMost(Duration.ofSeconds(20))
                .until(() -> orders.findById(order.getId()).orElseThrow().getStatus() == OrderStatus.OUT_FOR_DELIVERY);
        assertThat(processed.findById(new ProcessedEvent.Key(assigned.eventId(), DeliveryStatusProjector.CONSUMER)))
                .isPresent();
        assertThat(processed.findAll().stream()
                .filter(p -> p.getId().eventId().equals(assigned.eventId())).count()).isEqualTo(1);
    }

    @Test
    void poisonMessageGoesStraightToTheDeadLetterTopic() {
        String key = "poison-" + UUID.randomUUID();
        kafka.send(Topics.DELIVERY_STATUS, key, "{this is not json").join();

        var dead = pollFor(KafkaErrorHandling.dltTopic(Topics.DELIVERY_STATUS, "order-service"),
                r -> key.equals(r.key()));

        assertThat(dead).isPresent();
        assertThat(dead.get().value()).isEqualTo("{this is not json");
        assertThat(header(dead.get(), "kafka_dlt-exception-cause-fqcn")).contains("InvalidEventException");
    }

    @Test
    void cancellationIsPublishedAfterTheCreationForTheSameKey() {
        Order order = createOrder();
        orderService.cancel(order.getId(), "merchant request",
                new com.benzaazaa.parcel.support.security.AuthenticatedUser("ops", java.util.Set.of("OPS"), null, null));

        var rows = outbox.findByAggregateIdOrderBySeq(order.getId().toString());

        assertThat(rows).extracting(r -> r.getEventType())
                .containsExactly(EventTypes.ORDER_CREATED, EventTypes.ORDER_CANCELLED);
        var cancelled = pollFor(Topics.ORDER_EVENTS, r -> order.getId().toString().equals(r.key())
                && EventTypes.ORDER_CANCELLED.equals(header(r, EventHeaders.EVENT_TYPE)));
        assertThat(cancelled).isPresent();
    }
}
