package com.benzaazaa.parcel.support.messaging;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @EmbeddedId
    private Key id;

    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public Key getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Embeddable
    public record Key(UUID eventId, String consumer) implements Serializable {
    }
}
