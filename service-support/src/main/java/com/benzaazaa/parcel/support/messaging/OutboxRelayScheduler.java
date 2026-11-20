package com.benzaazaa.parcel.support.messaging;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/** Polling publisher. A separate bean so the @Transactional proxy on OutboxRelay is honoured. */
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${parcel.outbox.relay.fixed-delay-ms:500}")
    public void poll() {
        // drain quickly when there is a backlog, then sleep until the next tick
        int sent;
        do {
            sent = relay.publishBatch();
        } while (sent > 0);
    }

    @Scheduled(cron = "${parcel.outbox.purge-cron:0 15 3 * * *}")
    public void purge() {
        relay.purgePublishedOlderThan(Duration.ofDays(7));
    }
}
