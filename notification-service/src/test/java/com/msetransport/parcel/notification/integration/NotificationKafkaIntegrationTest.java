package com.msetransport.parcel.notification.integration;

import com.msetransport.parcel.events.EventCodec;
import com.msetransport.parcel.events.EventEnvelope;
import com.msetransport.parcel.events.Topics;
import com.msetransport.parcel.events.delivery.DeliveryStatusChangedV1;
import com.msetransport.parcel.events.delivery.DeliveryStatusV1;
import com.msetransport.parcel.notification.StubSmsProvider;
import com.msetransport.parcel.notification.domain.Channel;
import com.msetransport.parcel.notification.domain.DeliveryResult;
import com.msetransport.parcel.notification.domain.Notification;
import com.msetransport.parcel.notification.domain.NotificationRepository;
import com.msetransport.parcel.notification.sms.SmsConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "notification.fake-sms.enabled=false",
        "resilience4j.timelimiter.instances.smsGateway.timeout-duration=500ms",
        "resilience4j.retry.instances.smsGateway.wait-duration=20ms"})
class NotificationKafkaIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");
    static final StubSmsProvider SMS = new StubSmsProvider();

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("notification.sms.base-url", SMS::baseUrl);
    }

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventCodec codec;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    private EventEnvelope statusEvent(UUID orderId, DeliveryStatusV1 status, long seq) {
        return codec.wrap(new DeliveryStatusChangedV1(UUID.randomUUID(), orderId, "PX-ITNOTIF", UUID.randomUUID(),
                null, status, 1, null, "+212612345678", new BigDecimal("99.00"), seq, Instant.now()), "it-notif");
    }

    private void send(EventEnvelope e) {
        kafka.send(Topics.DELIVERY_STATUS, e.aggregateId(), codec.encode(e)).join();
    }

    private List<Notification> awaitNotifications(UUID orderId, int count) {
        return await().atMost(Duration.ofSeconds(30)).until(() -> notifications.findByOrderIdOrderByCreatedAt(orderId),
                l -> l.size() >= count);
    }

    @Test
    void statusEventIsSentOnLogAndSmsChannelsExactlyOnceEvenIfRedelivered() {
        SMS.mode(StubSmsProvider.Mode.OK);
        circuitBreakers.circuitBreaker(SmsConfig.INSTANCE).reset();
        UUID orderId = UUID.randomUUID();
        EventEnvelope outForDelivery = statusEvent(orderId, DeliveryStatusV1.OUT_FOR_DELIVERY, 1);

        send(outForDelivery);
        send(outForDelivery); // duplicate
        send(statusEvent(orderId, DeliveryStatusV1.DELIVERED, 2)); // marker: processed after the duplicate

        List<Notification> sent = awaitNotifications(orderId, 4);
        assertThat(notifications.findByEventId(outForDelivery.eventId()))
                .extracting(Notification::getChannel).containsExactlyInAnyOrder(Channel.LOG, Channel.SMS);
        assertThat(sent).hasSize(4).allMatch(n -> n.getStatus() == DeliveryResult.Status.SENT);
    }

    @Test
    void providerOutageOpensTheCircuitWhileLogChannelKeepsWorking() {
        CircuitBreaker breaker = circuitBreakers.circuitBreaker(SmsConfig.INSTANCE);
        breaker.reset();
        SMS.mode(StubSmsProvider.Mode.FAIL_503);
        UUID orderId = UUID.randomUUID();

        for (int i = 1; i <= 4; i++) {
            send(statusEvent(orderId, DeliveryStatusV1.FAILED, i));
        }

        List<Notification> all = awaitNotifications(orderId, 8);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(all).filteredOn(n -> n.getChannel() == Channel.LOG)
                .hasSize(4).allMatch(n -> n.getStatus() == DeliveryResult.Status.SENT);
        assertThat(all).filteredOn(n -> n.getChannel() == Channel.SMS)
                .extracting(Notification::getStatus).contains(DeliveryResult.Status.FAILED, DeliveryResult.Status.SKIPPED);
        SMS.mode(StubSmsProvider.Mode.OK);
        breaker.reset();
    }
}
