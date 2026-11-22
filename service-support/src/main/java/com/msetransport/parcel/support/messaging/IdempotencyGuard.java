package com.msetransport.parcel.support.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Idempotent consumer. Kafka gives at-least-once delivery (redelivery after rebalance, crash before
 * offset commit, outbox re-send). The processed_event insert runs in the SAME transaction as the
 * business change, so "effect applied" and "event marked processed" commit atomically.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public IdempotencyGuard(ProcessedEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return true if this is the first time {@code consumer} sees {@code eventId}; false for a duplicate. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(UUID eventId, String consumer) {
        boolean first = repository.insertIfAbsent(eventId, consumer, clock.instant()) == 1;
        if (!first) {
            log.info("Duplicate event {} for consumer {} skipped", eventId, consumer);
        }
        return first;
    }
}
