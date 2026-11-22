package com.msetransport.parcel.notification.sms;

import com.msetransport.parcel.notification.StubSmsProvider;
import com.msetransport.parcel.notification.domain.DeliveryResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real resilience composition against a stub HTTP provider. */
class SmsGatewayClientTest {

    private StubSmsProvider provider;
    private CircuitBreaker circuitBreaker;
    private SmsGatewayClient client;

    @BeforeEach
    void setUp() {
        provider = new StubSmsProvider();
        circuitBreaker = CircuitBreaker.of("sms", CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(HttpServerErrorException.class, TimeoutException.class, IOException.class,
                        ResourceAccessException.class)
                .build());
        Retry retry = Retry.of("sms", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10))
                .retryExceptions(HttpServerErrorException.class, TimeoutException.class, ResourceAccessException.class)
                .ignoreExceptions(CallNotPermittedException.class).build());
        TimeLimiter timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(300))
                .cancelRunningFuture(true).build());
        client = new SmsGatewayClient(RestClient.builder().baseUrl(provider.baseUrl()).build(), circuitBreaker, retry,
                timeLimiter, Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    @Test
    void successReturnsProviderReference() {
        DeliveryResult result = client.send("+212612345678", "hello");

        assertThat(result.status()).isEqualTo(DeliveryResult.Status.SENT);
        assertThat(result.providerRef()).isEqualTo("sms_1");
    }

    @Test
    void transientServerErrorIsRetried() {
        provider.mode(StubSmsProvider.Mode.FAIL_ONCE_THEN_OK);

        DeliveryResult result = client.send("+212612345678", "hello");

        assertThat(result.status()).isEqualTo(DeliveryResult.Status.SENT);
        assertThat(provider.hits()).isEqualTo(2);
    }

    @Test
    void clientErrorIsNotRetried() {
        provider.mode(StubSmsProvider.Mode.BAD_REQUEST);

        DeliveryResult result = client.send("+212000", "hello");

        assertThat(result.status()).isEqualTo(DeliveryResult.Status.FAILED);
        assertThat(provider.hits()).as("4xx means our request is wrong; retrying will not help").isEqualTo(1);
    }

    @Test
    void slowProviderIsCutOffByTheTimeLimiter() {
        provider.mode(StubSmsProvider.Mode.SLOW);
        long start = System.nanoTime();

        DeliveryResult result = client.send("+212612345678", "hello");

        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(result.status()).isEqualTo(DeliveryResult.Status.FAILED);
        assertThat(result.error()).contains("timeout");
        assertThat(elapsedMs).as("3 attempts x 300ms, far below 3 x 1.5s").isLessThan(1_400);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndThenFailsFastWithoutCallingTheProvider() {
        provider.mode(StubSmsProvider.Mode.FAIL_503);

        client.send("+212612345678", "1"); // 3 failed calls recorded (retries)
        client.send("+212612345678", "2"); // 4th call fills the window -> OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int hitsWhenOpened = provider.hits();

        DeliveryResult result = client.send("+212612345678", "3");

        assertThat(result.status()).isEqualTo(DeliveryResult.Status.SKIPPED);
        assertThat(result.error()).isEqualTo("circuit open");
        assertThat(provider.hits()).isEqualTo(hitsWhenOpened);
    }
}
