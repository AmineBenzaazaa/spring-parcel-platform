package com.benzaazaa.parcel.notification.integration;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.Topics;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusChangedV1;
import com.benzaazaa.parcel.events.delivery.DeliveryStatusV1;
import com.benzaazaa.parcel.events.order.OrderCreatedV1;
import com.benzaazaa.parcel.notification.live.ActivityEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * Real Kafka + Postgres + a real HTTP connection: an event published to the existing topics reaches
 * a browser-like SSE client through the live consumer group, is stored once for the timeline, and
 * the notification it triggers is pushed after commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "notification.sms.enabled=false",
        "notification.fake-sms.enabled=false"})
class LiveStreamIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @LocalServerPort
    int port;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    EventCodec codec;
    @Autowired
    ActivityEventRepository activity;
    @Autowired
    KafkaListenerEndpointRegistry listeners;
    @MockBean
    JwtDecoder jwtDecoder;

    private void send(String topic, EventEnvelope e) {
        kafka.send(topic, e.aggregateId(), codec.encode(e)).join();
    }

    @Test
    void kafkaEventsArePushedToAnSseClientAndStoredOnce() throws Exception {
        when(jwtDecoder.decode("stream-token")).thenReturn(Jwt.withTokenValue("stream-token").header("alg", "RS256")
                .subject("ops").claim("roles", List.of("OPS")).claim("token_use", "stream")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        // the live group starts at "latest": wait until it owns all 6 partitions (2 topics x 3)
        ContainerTestUtils.waitForAssignment(listeners.getListenerContainer("notification-live-activity"), 6);

        List<String> lines = new CopyOnWriteArrayList<>();
        HttpResponse<InputStream> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/activity/stream?access_token=stream-token"))
                .header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var in = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                in.lines().forEach(lines::add);
            } catch (Exception ignored) {
                // connection closed at the end of the test
            }
        });
        await().atMost(Duration.ofSeconds(10)).until(() -> lines.contains("event:hello"));

        UUID orderId = UUID.randomUUID();
        EventEnvelope created = codec.wrap(new OrderCreatedV1(orderId, "PX-LIVE0001", "M-1001", "Salma",
                "+212612345678", "12 Rue Atlas", "Casablanca", new BigDecimal("249.90"), Instant.now()), "live-it-1");
        EventEnvelope delivered = codec.wrap(new DeliveryStatusChangedV1(UUID.randomUUID(), orderId, "PX-LIVE0001",
                UUID.randomUUID(), DeliveryStatusV1.OUT_FOR_DELIVERY, DeliveryStatusV1.DELIVERED, 1, null,
                "+212612345678", new BigDecimal("249.90"), 3, Instant.now()), "live-it-1");
        send(Topics.ORDER_EVENTS, created);
        send(Topics.DELIVERY_STATUS, delivered);
        send(Topics.DELIVERY_STATUS, delivered); // redelivery: stored and pushed once

        // two independent consumer groups: the notification may arrive before the delivery event
        await().atMost(Duration.ofSeconds(30)).until(() -> lines.containsAll(
                List.of("event:order", "event:delivery", "event:notification")));
        assertThat(lines.stream().filter(l -> l.startsWith("data:") && l.contains("PX-LIVE0001")))
                .anyMatch(l -> l.contains("\"type\":\"ORDER_CREATED\"") && l.contains("\"correlationId\":\"live-it-1\""))
                .anyMatch(l -> l.contains("\"newStatus\":\"DELIVERED\""))
                .anyMatch(l -> l.contains("\"channel\":\"LOG\"") && l.contains("249.90 MAD"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(activity.findByOrderIdOrderByOccurredAtAscReceivedAtAsc(orderId))
                        .extracting(a -> a.getStatus()).containsExactly("CREATED", "DELIVERED"));
        assertThat(lines.stream().filter("event:delivery"::equals)).hasSize(1);
        reader.interrupt();
    }
}
