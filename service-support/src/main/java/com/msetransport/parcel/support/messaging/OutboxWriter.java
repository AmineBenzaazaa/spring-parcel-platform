package com.benzaazaa.parcel.support.messaging;

import com.benzaazaa.parcel.events.EventCodec;
import com.benzaazaa.parcel.events.EventEnvelope;
import com.benzaazaa.parcel.events.ParcelEvent;
import com.benzaazaa.parcel.support.correlation.CorrelationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Step 1 of the transactional outbox. Called from a business transaction; the event row commits or
 * rolls back together with the aggregate change. We never call Kafka here: a DB commit followed by a
 * failed Kafka send (or the reverse) is exactly the dual-write problem the outbox removes.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final EventCodec codec;

    public OutboxWriter(OutboxRepository repository, EventCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    /** MANDATORY: fails fast if someone calls it outside a transaction (that would break atomicity). */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String aggregateType, String topic, ParcelEvent event) {
        String correlationId = CorrelationId.currentOrNew();
        EventEnvelope envelope = codec.wrap(event, correlationId);
        repository.save(new OutboxEvent(envelope.eventId(), aggregateType, envelope.aggregateId(),
                envelope.eventType(), topic, codec.encode(envelope), correlationId, envelope.occurredAt()));
        return envelope.eventId();
    }
}
