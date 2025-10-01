package com.example.inventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;

    private UUID orderId;

    private String sku;

    private Long quantity;

    private String status;

    public Reservation(){};

    public Reservation(UUID orderId, String sku, Long quantity) {
        this.id = UUID.randomUUID();
        this.orderId=orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.status = "InventoryReserved";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrder_id(UUID orderId) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
