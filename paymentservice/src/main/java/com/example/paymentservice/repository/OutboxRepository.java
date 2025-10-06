package com.example.paymentservice.repository;

import com.example.paymentservice.model.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, String> {
    List<Outbox> findTop5ByStatus(String pending);
}
