package com.msetransport.parcel.notification.sms;

import com.msetransport.parcel.notification.domain.DeliveryResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Calls the external SMS provider, wrapped (outermost first) in:
 * <ol>
 *   <li><b>Retry</b>: transient errors (5xx, IO, timeout) are retried with exponential backoff;</li>
 *   <li><b>CircuitBreaker</b>: when most recent calls fail, stop calling for a while (fail fast,
 *       let the provider recover, keep our consumer threads free);</li>
 *   <li><b>TimeLimiter</b>: a slow provider cannot hold a thread longer than the timeout.</li>
 * </ol>
 * Same order as Resilience4j's Spring annotations (Retry wraps CircuitBreaker wraps TimeLimiter),
 * written programmatically so the composition is explicit and unit-testable.
 */
public class SmsGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(SmsGatewayClient.class);

    private final RestClient http;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public SmsGatewayClient(RestClient http, CircuitBreaker circuitBreaker, Retry retry, TimeLimiter timeLimiter,
                            ExecutorService executor) {
        this.http = http;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    public DeliveryResult send(String to, String text) {
        Callable<SmsResponse> timed = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> CompletableFuture.supplyAsync(() -> post(to, text), executor));
        Callable<SmsResponse> guarded = Retry.decorateCallable(retry,
                CircuitBreaker.decorateCallable(circuitBreaker, timed));
        try {
            SmsResponse response = guarded.call();
            return DeliveryResult.sent(response == null ? null : response.id());
        } catch (CallNotPermittedException e) {
            log.warn("SMS skipped: circuit breaker '{}' is {}", circuitBreaker.getName(), circuitBreaker.getState());
            return DeliveryResult.skipped("circuit open");
        } catch (TimeoutException e) {
            return DeliveryResult.failed("timeout after " + timeLimiter.getTimeLimiterConfig().getTimeoutDuration());
        } catch (Exception e) {
            log.warn("SMS failed after retries: {}", e.toString());
            return DeliveryResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private SmsResponse post(String to, String text) {
        return http.post().uri("/v1/messages")
                .body(Map.of("to", to, "text", text))
                .retrieve()
                .body(SmsResponse.class);
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    public record SmsResponse(String id, String status) {
    }
}
