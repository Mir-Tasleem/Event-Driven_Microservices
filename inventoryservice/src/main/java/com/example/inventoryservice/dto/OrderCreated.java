package com.example.inventoryservice.dto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OrderCreated {
    private UUID id;
    private String status;
    private LocalDateTime createdAt;
    private UUID customerId;
    private List<InventoryOrderItem> orderItems;
    private double totalAmount;


    @JsonCreator
    public OrderCreated(
            @JsonProperty("id") UUID id,
            @JsonProperty("status") String status,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("customerId") UUID customerId,
            @JsonProperty("orderItems") List<InventoryOrderItem> orderItems,
            @JsonProperty("totalAmount") double totalAmount
    ) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.customerId = customerId;
        this.orderItems = orderItems;
        this.totalAmount = totalAmount;
    }

    // getters (required)
    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public UUID getCustomerId() { return customerId; }
    public List<InventoryOrderItem> getOrderItems() { return orderItems; }
    public double getTotalAmount() { return totalAmount; }
}
