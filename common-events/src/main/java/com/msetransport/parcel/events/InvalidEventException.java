package com.benzaazaa.parcel.events;

/**
 * The message can never be processed (malformed JSON, missing fields, unknown type).
 * Consumers treat it as non-retryable: it goes straight to the dead-letter topic.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
