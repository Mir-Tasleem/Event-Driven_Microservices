package com.example.orderservice.service;

import com.example.orderservice.dto.OrderItemDTO;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     *
     * @param orderRequest
     * @return
     * @throws JsonProcessingException
     * This method is used to create the order
     */
    @Transactional(rollbackFor = Exception.class)
    public UUID createOrder(OrderRequest orderRequest) throws JsonProcessingException {
        List<OrderItem> items=new ArrayList<>();
        UUID id=UUID.randomUUID();

        //create order
        Order order=new Order();
        order.setId(id);
        order.setCustomerId(orderRequest.getCustomerId());
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        orderRequest.getItems().forEach(orderItem -> {
            OrderItem item=new OrderItem(orderItem.getSku(),orderItem.getQty(),orderItem.getPrice());
            item.setOrder(order);
            items.add(item);
        });
        order.setOrderItems(items);
        order.setTotalAmount(calculateTotalAmount(orderRequest.getItems()));
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

    /**
     *
     * @param orderItems
     * @return
     * This method is used to calculate the total amount of the order
     */
    private double calculateTotalAmount(List<OrderItemDTO> orderItems) {
        return orderItems.stream()
                .mapToDouble(item->item.getPrice()*item.getQty())
                .sum();
    }

    /**
     *
     * @param id
     * @return
     * This method is used to get the order by id
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        Order order=orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException("Order with orderId: "+id+" not found"));
        OrderResponse orderResponse=new OrderResponse();
        orderResponse.setId(order.getId());
        orderResponse.setCustomerId(order.getCustomerId());
        orderResponse.setOrderItems(order.getOrderItems());
        orderResponse.setAmount(order.getTotalAmount());
        orderResponse.setStatus(order.getStatus());
        return orderResponse;
    }
}
