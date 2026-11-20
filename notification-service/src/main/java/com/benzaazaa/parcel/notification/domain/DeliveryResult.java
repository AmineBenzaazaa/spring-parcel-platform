package com.benzaazaa.parcel.notification.domain;

/** Outcome of one send attempt on one channel. */
public record DeliveryResult(Status status, String providerRef, String error) {

    public enum Status {
        SENT,
        FAILED,
        /** Not attempted, e.g. circuit breaker open: fail fast instead of piling up on a dead provider. */
        SKIPPED
    }

    public static DeliveryResult sent(String providerRef) {
        return new DeliveryResult(Status.SENT, providerRef, null);
    }

    public static DeliveryResult failed(String error) {
        return new DeliveryResult(Status.FAILED, null, truncate(error));
    }

    public static DeliveryResult skipped(String why) {
        return new DeliveryResult(Status.SKIPPED, null, truncate(why));
    }

    private static String truncate(String s) {
        return s == null ? null : s.substring(0, Math.min(s.length(), 255));
    }
}
