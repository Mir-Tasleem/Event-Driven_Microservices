package com.example.orderservice.model;

import jakarta.persistence.Id;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProcessedEvent {
    @Id
    private UUID eventId;

    private LocalDateTime recieivedAt;

    public ProcessedEvent(UUID uuid) {
        this.eventId=uuid;
        this.recieivedAt=LocalDateTime.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getRecieivedAt() {
        return recieivedAt;
    }

    public void setRecieivedAt(LocalDateTime recieivedAt) {
        this.recieivedAt = recieivedAt;
    }
}
