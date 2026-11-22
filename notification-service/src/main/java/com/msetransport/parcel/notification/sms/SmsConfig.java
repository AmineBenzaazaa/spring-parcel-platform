package com.benzaazaa.parcel.notification.sms;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Policies come from application.yml (resilience4j.*.instances.smsGateway) via the Boot registries. */
@Configuration
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")
public class SmsConfig {

    public static final String INSTANCE = "smsGateway";

    @Bean(destroyMethod = "shutdown")
    ExecutorService smsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    SmsGatewayClient smsGatewayClient(RestClient.Builder builder,
                                      @Value("${notification.sms.base-url}") String baseUrl,
                                      @Value("${notification.sms.connect-timeout:1s}") Duration connectTimeout,
                                      @Value("${notification.sms.read-timeout:3s}") Duration readTimeout,
                                      CircuitBreakerRegistry circuitBreakers, RetryRegistry retries,
                                      TimeLimiterRegistry timeLimiters, ExecutorService smsExecutor) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        RestClient http = builder.baseUrl(baseUrl).requestFactory(factory).build();
        return new SmsGatewayClient(http, circuitBreakers.circuitBreaker(INSTANCE), retries.retry(INSTANCE),
                timeLimiters.timeLimiter(INSTANCE), smsExecutor);
    }
}
