package com.benzaazaa.parcel.notification.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Picks up our entities plus the outbox/processed_event entities from service-support.
 * Kept off the main class so @WebMvcTest slices do not try to build repositories.
 */
@Configuration
@EntityScan({"com.benzaazaa.parcel.notification", "com.benzaazaa.parcel.support"})
@EnableJpaRepositories({"com.benzaazaa.parcel.notification", "com.benzaazaa.parcel.support"})
public class PersistenceConfig {
}
