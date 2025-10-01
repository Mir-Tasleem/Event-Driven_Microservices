package com.example.inventoryservice.dto;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryOrderItem {
    private UUID orderId;

    private String sku;

    private Long quantity;

    public InventoryOrderItem() {}

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

}
