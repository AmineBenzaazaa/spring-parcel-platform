package com.benzaazaa.parcel.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** auth-service only reuses the web/correlation parts of service-support (no Kafka, no outbox). */
@SpringBootApplication(scanBasePackages = {
        "com.benzaazaa.parcel.auth",
        "com.benzaazaa.parcel.support.core",
        "com.benzaazaa.parcel.support.web",
        "com.benzaazaa.parcel.support.correlation"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
