package com.msetransport.parcel.support.correlation;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A correlation id follows one business request across HTTP hops and Kafka hops. It lives in the
 * SLF4J MDC (so every log line carries it), in the {@code X-Correlation-Id} HTTP header and in the
 * {@code x-correlation-id} Kafka header.
 */
public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrNew() {
        String id = current();
        return id != null ? id : newId();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** Accepts a client-supplied id only if it is short and log-safe (prevents log injection). */
    public static String sanitize(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches() ? candidate : newId();
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
