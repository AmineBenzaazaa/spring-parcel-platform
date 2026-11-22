package com.msetransport.parcel.notification.sms;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stand-in for a third-party SMS provider so the demo runs offline. Switch its mode to watch the
 * circuit breaker react:  POST /fake-sms/admin/mode/FAIL | SLOW | OK
 */
@Hidden
@RestController
@RequestMapping("/fake-sms")
@ConditionalOnProperty(name = "notification.fake-sms.enabled", havingValue = "true")
public class FakeSmsProviderController {

    public enum Mode { OK, FAIL, SLOW }

    private static final Logger log = LoggerFactory.getLogger(FakeSmsProviderController.class);
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.OK);

    @PostMapping("/v1/messages")
    public ResponseEntity<Map<String, String>> send(@RequestBody Map<String, String> body) throws InterruptedException {
        switch (mode.get()) {
            case FAIL -> {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "provider down"));
            }
            case SLOW -> Thread.sleep(5_000);
            case OK -> { }
        }
        String id = "sms_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("FAKE-SMS provider accepted {} for {}", id, body.get("to"));
        return ResponseEntity.accepted().body(Map.of("id", id, "status", "QUEUED"));
    }

    @PostMapping("/admin/mode/{mode}")
    public Map<String, String> setMode(@PathVariable Mode mode) {
        this.mode.set(mode);
        log.warn("FAKE-SMS provider mode set to {}", mode);
        return Map.of("mode", mode.name());
    }
}
