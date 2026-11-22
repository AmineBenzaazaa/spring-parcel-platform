package com.msetransport.parcel.notification.live;

import com.msetransport.parcel.notification.application.NotificationRecorded;
import com.msetransport.parcel.notification.live.LivePayloads.CircuitLive;
import com.msetransport.parcel.notification.live.LivePayloads.NotificationLive;
import com.msetransport.parcel.notification.sms.SmsConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process sources of live events: notifications (pushed only after their transaction commits,
 * so the UI never shows a row that was rolled back) and circuit breaker state transitions.
 */
@Component
public class LiveBridges {

    private final LiveEventHub hub;
    private final CircuitBreaker smsBreaker;

    public LiveBridges(LiveEventHub hub, ObjectProvider<CircuitBreakerRegistry> registry) {
        this.hub = hub;
        CircuitBreakerRegistry r = registry.getIfAvailable();
        this.smsBreaker = r == null ? null : r.circuitBreaker(SmsConfig.INSTANCE);
        if (smsBreaker != null) {
            smsBreaker.getEventPublisher().onStateTransition(e -> hub.broadcast(
                    new LiveEvent(LiveEvent.CIRCUIT, circuit())));
        }
    }

    @TransactionalEventListener
    public void onNotification(NotificationRecorded event) {
        hub.broadcast(new LiveEvent(LiveEvent.NOTIFICATION, NotificationLive.from(event.notification())));
    }

    public CircuitLive circuit() {
        if (smsBreaker == null) {
            return new CircuitLive(SmsConfig.INSTANCE, "DISABLED", -1, 0);
        }
        var metrics = smsBreaker.getMetrics();
        return new CircuitLive(smsBreaker.getName(), smsBreaker.getState().name(), metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls());
    }
}
