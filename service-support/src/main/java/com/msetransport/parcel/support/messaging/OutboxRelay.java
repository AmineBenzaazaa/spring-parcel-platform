package com.benzaazaa.parcel.support.messaging;

import com.benzaazaa.parcel.events.EventHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Step 2 of the transactional outbox: publish committed rows to Kafka, then mark them published.
 *
 * <p>Delivery is at-least-once: if the process dies after Kafka acked but before the UPDATE commits,
 * the row is sent again on the next run. That is why every consumer is idempotent on eventId.
 * On the first failure the batch stops, so a later event of the same order never overtakes an
 * earlier one.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Counter published;
    private final Counter failures;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, Clock clock,
                       MeterRegistry meters,
                       int batchSize, Duration sendTimeout) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.published = Counter.builder("parcel.outbox.published").description("Outbox rows sent to Kafka")
                .register(meters);
        this.failures = Counter.builder("parcel.outbox.failures").description("Outbox send failures")
                .register(meters);
        Gauge.builder("parcel.outbox.pending", repository, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox rows waiting to be published").register(meters);
    }

    /** @return number of rows published in this run */
    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                kafka.send(toRecord(event)).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
                published.increment();
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.recordFailure("interrupted");
                break;
            } catch (Exception e) {
                failures.increment();
                event.recordFailure(e.getMessage());
                log.warn("Outbox publish failed for event {} ({}), will retry: {}", event.getId(),
                        event.getEventType(), e.getMessage());
                break;
            }
        }
        if (sent > 0) {
            log.debug("Outbox relay published {} event(s)", sent);
        }
        return sent;
    }

    @Transactional
    public int purgePublishedOlderThan(Duration retention) {
        return repository.deletePublishedBefore(clock.instant().minus(retention));
    }

    static ProducerRecord<String, String> toRecord(OutboxEvent event) {
        // key = aggregateId -> all events of one order land on the same partition, in order
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
        record.headers().add(EventHeaders.EVENT_ID, bytes(event.getId().toString()));
        record.headers().add(EventHeaders.EVENT_TYPE, bytes(event.getEventType()));
        if (event.getCorrelationId() != null) {
            record.headers().add(EventHeaders.CORRELATION_ID, bytes(event.getCorrelationId()));
        }
        return record;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
