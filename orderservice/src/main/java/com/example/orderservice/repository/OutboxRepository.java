package com.example.orderservice.repository;

import com.example.orderservice.model.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
    List<Outbox> findTop5ByStatus(String status);
}
