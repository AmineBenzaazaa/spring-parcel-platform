package com.msetransport.parcel.support.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CoreConfig {

    /** Injected everywhere instead of Instant.now() so time-based rules are unit-testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
