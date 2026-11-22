package com.msetransport.parcel.support.messaging;

import com.msetransport.parcel.events.EventHeaders;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final OutboxRelay relay = new OutboxRelay(repository, kafka, Clock.fixed(NOW, ZoneOffset.UTC), meters,
            100, Duration.ofSeconds(1));

    private static OutboxEvent row(String aggregateId) {
        return new OutboxEvent(UUID.randomUUID(), "Order", aggregateId, "OrderCreated", "parcel.orders.v1",
                "{}", "corr-1", NOW);
    }

    @Test
    void publishesEachRowAndMarksItPublished() {
        var a = row("order-1");
        var b = row("order-2");
        when(repository.lockNextBatch(100)).thenReturn(List.of(a, b));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        int sent = relay.publishBatch();

        assertThat(sent).isEqualTo(2);
        assertThat(a.getPublishedAt()).isEqualTo(NOW);
        assertThat(b.getPublishedAt()).isEqualTo(NOW);
        assertThat(meters.counter("parcel.outbox.published").count()).isEqualTo(2.0);
    }

    @Test
    void stopsAtFirstFailureSoLaterEventsDoNotOvertakeEarlierOnes() {
        var a = row("order-1");
        var b = row("order-1");
        when(repository.lockNextBatch(100)).thenReturn(List.of(a, b));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        int sent = relay.publishBatch();

        assertThat(sent).isZero();
        assertThat(a.getPublishedAt()).isNull();
        assertThat(a.getLastError()).contains("broker down");
        assertThat(b.getAttempts()).isZero();
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void recordUsesAggregateIdAsKeyAndCarriesHeaders() {
        var event = row("order-9");

        ProducerRecord<String, String> record = OutboxRelay.toRecord(event);

        assertThat(record.key()).isEqualTo("order-9");
        assertThat(record.topic()).isEqualTo("parcel.orders.v1");
        assertThat(new String(record.headers().lastHeader(EventHeaders.EVENT_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo(event.getId().toString());
        assertThat(new String(record.headers().lastHeader(EventHeaders.CORRELATION_ID).value(),
                StandardCharsets.UTF_8)).isEqualTo("corr-1");
    }
}
