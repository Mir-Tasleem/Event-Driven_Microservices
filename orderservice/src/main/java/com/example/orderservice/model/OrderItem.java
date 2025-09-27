package com.example.orderservice.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name="order_items")
public class OrderItem {
    @Id
    private UUID orderId;

    private String sku;

    private int quantity;

    private double price;

    @ManyToOne
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    private Order order;

    public OrderItem(String sku, int quantity, double price) {
        this.orderId=UUID.randomUUID();
        this.sku=sku;
        this.quantity=quantity;
        this.price=price;
    }

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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}
