package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByIdempotentKey(String idempotencyKey);
}
