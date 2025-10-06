package com.example.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OrderFinal {
    private UUID id;
    private String status;
    private LocalDateTime createdAt;
    private UUID customerId;
    private List<OrderItem> orderItems;
    private double totalAmount;

    @JsonCreator
    public OrderFinal(
            @JsonProperty("id") UUID id,
            @JsonProperty("status") String status,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("customerId") UUID customerId,
            @JsonProperty("orderItems") List<OrderItem> orderItems,
            @JsonProperty("totalAmount") double totalAmount
    ) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.customerId = customerId;
        this.orderItems = orderItems;
        this.totalAmount = totalAmount;
    }

    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public UUID getCustomerId() { return customerId; }
    public List<OrderItem> getOrderItems() { return orderItems; }
    public double getTotalAmount() { return totalAmount; }
}
