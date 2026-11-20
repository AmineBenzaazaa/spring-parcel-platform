package com.benzaazaa.parcel.notification.live;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Spring Boot has no Kafka health indicator, so the dashboard's "Kafka" light comes from here:
 * {@code /actuator/health/kafka} asks the cluster for its nodes with a short timeout.
 */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator, DisposableBean {

    private static final int TIMEOUT_MS = 2000;

    private final KafkaAdmin kafkaAdmin;
    private volatile AdminClient admin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try {
            var cluster = client().describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS));
            int nodes = cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            return Health.up().withDetail("clusterId", cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .withDetail("nodes", nodes).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }

    private AdminClient client() {
        if (admin == null) {
            synchronized (this) {
                if (admin == null) {
                    admin = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                }
            }
        }
        return admin;
    }

    @Override
    public void destroy() {
        if (admin != null) {
            admin.close();
        }
    }
}
