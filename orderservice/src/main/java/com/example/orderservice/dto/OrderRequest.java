package com.example.orderservice.dto;

import com.example.orderservice.model.OrderItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OrderRequest {
    private UUID customerId;

    private List<OrderItemDTO> items;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public List<OrderItemDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDTO> items) {
        if (items != null) {
            this.items = new ArrayList<>(items);
        } else {
            this.items = new ArrayList<>();
        }
    }

    @Override
    public String toString() {
        return "OrderRequest{" +
                "customerId=" + customerId +
                ", items=" + items +
                '}';
    }
}
