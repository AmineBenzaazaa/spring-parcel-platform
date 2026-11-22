package com.msetransport.parcel.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** auth-service only reuses the web/correlation parts of service-support (no Kafka, no outbox). */
@SpringBootApplication(scanBasePackages = {
        "com.msetransport.parcel.auth",
        "com.msetransport.parcel.support.core",
        "com.msetransport.parcel.support.web",
        "com.msetransport.parcel.support.correlation"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
