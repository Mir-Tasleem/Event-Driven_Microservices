package com.example.orderservice.service;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    private OrderRepository orderRepository;
    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;

//    private KafkaTemplate<String, String> kafkaTemplate;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public UUID createOrder(OrderRequest orderRequest, String idempotencyKey) throws JsonProcessingException {
        //check idempotency
        Optional<Order> existing=orderRepository.findByIdempotentKey(idempotencyKey);
        if(existing.isPresent()){
            return existing.get().getId();
        }

        idempotencyKey=UUID.randomUUID().toString();

        UUID id=UUID.randomUUID();

        //create order
        Order order=new Order();
        order.setId(id);
        order.setCustomerId(orderRequest.getCustomerId());
        order.setStatus("PENDING");
        order.setIdempotencyKey(idempotencyKey);
        order.setTotalAmount(calculateTotalAmount(orderRequest.getOrderItems()));
        order.setCreatedAt(LocalDateTime.now());
        orderRequest.getOrderItems().forEach(orderItem -> {
            order.getOrderItems().add(new OrderItem(orderItem.getSku(),orderItem.getQuantity(),orderItem.getPrice()));
        });
        orderRepository.save(order);

        //create outbox event
        Outbox outbox=new Outbox();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateId(order.getId());
        outbox.setType("OrderCreated");
        outbox.setStatus("PENDING");
        outbox.setPayload(objectMapper.writeValueAsString(order));
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);

        return order.getId();
    }

    private double calculateTotalAmount(List<OrderItem> orderItems) {
        return orderItems.stream()
                .mapToDouble(item->item.getPrice()*item.getQuantity())
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
