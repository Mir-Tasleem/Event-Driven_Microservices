package com.example.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderItem {
    private String sku;
    private double price;
    private long quantity;

    @JsonCreator
    public OrderItem(
            @JsonProperty("sku") String sku,
            @JsonProperty("price") double price,
            @JsonProperty("quantity") long quantity
    ) {
        this.sku = sku;
        this.price = price;
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public double getPrice() { return price; }
    public long getQuantity() { return quantity; }
}
