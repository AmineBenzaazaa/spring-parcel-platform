package com.benzaazaa.parcel.notification.live;

/**
 * What goes down the SSE pipe: {@code event: <name>} plus a JSON {@code data:} line.
 * Names: {@code order}, {@code delivery}, {@code notification}, {@code circuit}, {@code hello}.
 */
public record LiveEvent(String name, Object data) {

    public static final String ORDER = "order";
    public static final String DELIVERY = "delivery";
    public static final String NOTIFICATION = "notification";
    public static final String CIRCUIT = "circuit";
    public static final String HELLO = "hello";
}
