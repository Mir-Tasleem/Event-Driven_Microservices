package com.example.inventoryservice.dto;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderCreated {
    private UUID id;

    private UUID customerId;

    private String status;

    private double totalAmount;

    private LocalDateTime createdAt;

    List<InventoryOrderItem> orderItems=new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public  OrderCreated(){}
    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<InventoryOrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<InventoryOrderItem> orderItems) {
        this.orderItems = orderItems;
    }

}
