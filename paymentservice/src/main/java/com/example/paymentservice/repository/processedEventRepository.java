package com.example.paymentservice.repository;

import com.example.inventoryservice.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface processedEventRepository extends JpaRepository<ProcessedEvent, String> {
    boolean findByEventId(UUID orderId);
}
