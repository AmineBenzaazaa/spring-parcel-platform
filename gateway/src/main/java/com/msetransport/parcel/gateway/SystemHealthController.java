package com.msetransport.parcel.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One call for the dashboard's health strip: the gateway fans out to every service's actuator
 * health (in parallel, 2 s timeout each) plus notification-service's Kafka check. Services' actuator
 * ports stay private; only this summary (status + latency, no details) leaves the cluster.
 */
@RestController
public class SystemHealthController {

    static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final WebClient http;
    private final Map<String, String> targets;

    public SystemHealthController(WebClient.Builder builder,
                                  @Value("${AUTH_URI:http://localhost:8081}") String auth,
                                  @Value("${ORDER_URI:http://localhost:8082}") String order,
                                  @Value("${DISPATCH_URI:http://localhost:8083}") String dispatch,
                                  @Value("${NOTIFICATION_URI:http://localhost:8084}") String notification) {
        this.http = builder.build();
        this.targets = new LinkedHashMap<>();
        targets.put("auth-service", auth + "/actuator/health");
        targets.put("order-service", order + "/actuator/health");
        targets.put("dispatch-service", dispatch + "/actuator/health");
        targets.put("notification-service", notification + "/actuator/health");
        targets.put("kafka", notification + "/actuator/health/kafka");
    }

    @GetMapping("/api/system/health")
    public Mono<SystemHealth> health() {
        Mono<Component> self = Mono.just(new Component("gateway", "UP", 0));
        Flux<Component> downstream = Flux.fromIterable(targets.entrySet())
                .flatMapSequential(e -> probe(e.getKey(), e.getValue()));
        return Flux.concat(self, downstream).collectList().map(components -> {
            boolean allUp = components.stream().allMatch(c -> "UP".equals(c.status()));
            boolean anyUp = components.stream().anyMatch(c -> "UP".equals(c.status()));
            String overall = allUp ? "UP" : anyUp ? "DEGRADED" : "DOWN";
            return new SystemHealth(overall, Instant.now(), components);
        });
    }

    private Mono<Component> probe(String name, String url) {
        long start = System.nanoTime();
        return http.get().uri(url)
                .exchangeToMono(response -> response.bodyToMono(Map.class)
                        .map(body -> String.valueOf(body.getOrDefault("status", "UNKNOWN")))
                        .defaultIfEmpty(response.statusCode().is2xxSuccessful() ? "UP" : "DOWN"))
                .timeout(TIMEOUT)
                .onErrorReturn("DOWN")
                .map(status -> new Component(name, status, (System.nanoTime() - start) / 1_000_000));
    }

    public record Component(String name, String status, long latencyMs) {
    }

    public record SystemHealth(String status, Instant checkedAt, List<Component> components) {
    }
}
