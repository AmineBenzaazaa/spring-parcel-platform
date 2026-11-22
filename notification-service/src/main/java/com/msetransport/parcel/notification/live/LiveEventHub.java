package com.benzaazaa.parcel.notification.live;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fan-out of live events to connected browsers over Server-Sent Events.
 *
 * <p>Each {@link SseEmitter} is an async servlet response: an open connection costs a socket and a
 * small object, not a thread. A failed write means the client went away, so the emitter is dropped.
 * A comment line every 15 s keeps proxies (nginx, the gateway) from closing idle connections and
 * lets us notice dead clients.
 */
@Component
public class LiveEventHub {

    private static final Logger log = LoggerFactory.getLogger(LiveEventHub.class);
    static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    public LiveEventHub(MeterRegistry meters) {
        Gauge.builder("parcel.live.subscribers", emitters, List::size)
                .description("Browsers connected to the live event stream").register(meters);
    }

    public SseEmitter subscribe(LiveEvent hello) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        send(emitter, hello);
        return emitter;
    }

    public void broadcast(LiveEvent event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
        }
    }

    public int subscribers() {
        return emitters.size();
    }

    @Scheduled(fixedRateString = "${parcel.live.heartbeat-ms:15000}")
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                drop(emitter);
            }
        }
    }

    private void send(SseEmitter emitter, LiveEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(sequence.incrementAndGet()))
                    .name(event.name())
                    .data(event.data(), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            drop(emitter);
        }
    }

    private void drop(SseEmitter emitter) {
        emitters.remove(emitter);
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // already closed
        }
        log.debug("Live stream client disconnected ({} left)", emitters.size());
    }
}
