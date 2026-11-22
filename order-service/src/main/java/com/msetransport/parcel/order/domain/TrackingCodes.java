package com.msetransport.parcel.order.domain;

import java.security.SecureRandom;

/** Human-friendly tracking codes like PX-7KQ2M9XA (no 0/O/1/I to avoid misreading on the phone). */
public final class TrackingCodes {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TrackingCodes() {
    }

    public static String next() {
        StringBuilder sb = new StringBuilder("PX-");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
