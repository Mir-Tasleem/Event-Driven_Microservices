package com.example.orderservice.service;

import com.example.orderservice.dto.OrderItemDTO;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    private OrderRepository orderRepository;
    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID createOrder(OrderRequest orderRequest) throws JsonProcessingException {
        List<OrderItem> items=new ArrayList<>();
        UUID id=UUID.randomUUID();

        //create order
        Order order=new Order();
        order.setId(id);
        order.setCustomerId(orderRequest.getCustomerId());
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        System.out.println(orderRequest);
        orderRequest.getItems().forEach(orderItem -> {
            OrderItem item=new OrderItem(orderItem.getSku(),orderItem.getQty(),orderItem.getPrice());
            item.setOrder(order);
            items.add(item);
        });
        order.setOrderItems(items);
        System.out.println(items);
        order.setTotalAmount(calculateTotalAmount(orderRequest.getItems()));
        orderRepository.save(order);


        //create outbox event
        Outbox outbox=new Outbox();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateId(order.getId());
        outbox.setType("OrderCreated");
        outbox.setStatus("PENDING");
        outbox.setPayload(order);
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);

        return order.getId();
    }

    private double calculateTotalAmount(List<OrderItemDTO> orderItems) {
        return orderItems.stream()
                .mapToDouble(item->item.getPrice()*item.getQty())
                .sum();
    }

    public OrderResponse getOrderById(UUID id) {
        Order order=orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        OrderResponse orderResponse=new OrderResponse();
        orderResponse.setId(order.getId());
        orderResponse.setCustomerId(order.getCustomerId());
        orderResponse.setOrderItems(order.getOrderItems());
        orderResponse.setAmount(order.getTotalAmount());
        return orderResponse;
    }
}
