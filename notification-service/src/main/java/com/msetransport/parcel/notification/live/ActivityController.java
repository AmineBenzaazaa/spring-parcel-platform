package com.msetransport.parcel.notification.live;

import com.msetransport.parcel.notification.live.LivePayloads.CircuitLive;
import com.msetransport.parcel.notification.live.LivePayloads.Hello;
import com.msetransport.parcel.notification.live.LivePayloads.TimelineEntry;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('OPS')")
public class ActivityController {

    static final String TOKEN_USE_CLAIM = "token_use";
    static final String TOKEN_USE_STREAM = "stream";

    private final LiveEventHub hub;
    private final LiveBridges bridges;
    private final ActivityService activity;
    private final Clock clock;
    private final String instance;

    public ActivityController(LiveEventHub hub, LiveBridges bridges, ActivityService activity, Clock clock,
                              @Value("${HOSTNAME:local}") String instance) {
        this.hub = hub;
        this.bridges = bridges;
        this.activity = activity;
        this.clock = clock;
        this.instance = instance;
    }

    /**
     * Server-Sent Events: order, delivery, notification and circuit events as they happen.
     * EventSource cannot send headers, so the token arrives as {@code ?access_token=}; only a
     * 60-second stream token (token_use=stream) is accepted here, never a full session token.
     */
    @Operation(summary = "Ops: live event stream (SSE). Authenticate with ?access_token=<stream token>")
    @GetMapping(path = "/api/activity/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal Jwt jwt) {
        if (!TOKEN_USE_STREAM.equals(jwt.getClaimAsString(TOKEN_USE_CLAIM))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SseEmitter emitter = hub.subscribe(new LiveEvent(LiveEvent.HELLO,
                new Hello(instance, clock.instant(), bridges.circuit())));
        // tell nginx not to buffer the stream
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").header("Cache-Control", "no-cache")
                .body(emitter);
    }

    @Operation(summary = "Ops: every event seen for one order, oldest first, with correlation ids")
    @GetMapping("/api/activity/orders/{orderId}")
    public List<TimelineEntry> timeline(@PathVariable UUID orderId) {
        return activity.timeline(orderId);
    }

    @Operation(summary = "Ops: state of the SMS provider circuit breaker")
    @GetMapping("/api/notifications/circuit-breaker")
    public CircuitLive circuitBreaker() {
        return bridges.circuit();
    }
}
