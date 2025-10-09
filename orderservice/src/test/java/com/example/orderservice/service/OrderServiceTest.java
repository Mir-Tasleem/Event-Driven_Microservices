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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;  // Mock the repository

    @Mock
    private OutboxRepository outboxRepository;  // Mock the outbox repository

    @Mock
    private ObjectMapper objectMapper;  // Mock the ObjectMapper

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<Outbox> outboxCaptor;

    @InjectMocks
    private OrderService orderService;  // Inject mocks into OrderService

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    @Test
    void testCreateOrder_success() throws JsonProcessingException {
        UUID customerId = UUID.randomUUID();

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(Outbox.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderItemDTO item = new OrderItemDTO();
        item.setSku("SKU789");
        item.setQty(3);
        item.setPrice(15.0);
        OrderRequest orderreq = new OrderRequest();
        orderreq.setCustomerId(customerId);
        orderreq.setItems(Collections.singletonList(item));

        UUID orderId = orderService.createOrder(orderreq);

        assertNotNull(orderId);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        verify(outboxRepository, times(1)).save(outboxCaptor.capture());
        
        Order savedOrder = orderCaptor.getValue();
        Outbox savedOutbox = outboxCaptor.getValue();

        assertEquals(customerId, savedOrder.getCustomerId());
        assertEquals("PENDING", savedOrder.getStatus());

        assertNotNull(savedOutbox);
        assertEquals(orderId, savedOutbox.getAggregateId());
        assertEquals("OrderCreated", savedOutbox.getType());
        assertEquals("PENDING", savedOutbox.getStatus());
    }

    @Test
    void testCreateOrder_ThrowsException() throws JsonProcessingException {
        UUID customerId = UUID.randomUUID();

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(Outbox.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderItemDTO item = new OrderItemDTO();
        item.setSku("SKU789");
        item.setQty(3);
        item.setPrice(15.0);
        OrderRequest orderreq = new OrderRequest();
        orderreq.setCustomerId(customerId);
        orderreq.setItems(Collections.singletonList(item));

        UUID orderId = orderService.createOrder(orderreq);

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("test") {});
        OrderService faultyService = new OrderService(orderRepository, outboxRepository, objectMapper);

        assertThrows(JsonProcessingException.class, () -> faultyService.createOrder(orderreq));
    }

    @Test
    void testGetOrderById_suceess() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        OrderItem item = new OrderItem("SKU789", 3, 15.0);
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setOrderItems(Collections.singletonList(item));
        order.setTotalAmount(45.0);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId);

        assertEquals(orderId, response.getId());
        assertEquals(customerId, response.getCustomerId());
        assertEquals("PENDING", response.getStatus());
        assertEquals(45.0, response.getAmount());
        assertEquals(1, response.getOrderItems().size());

        verify(orderRepository, times(1)).findById(orderId);
    }

    @Test
    void testGetOrderById_ThrowsException() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findById(orderId)).thenThrow(new RuntimeException("test"));
        OrderService faultyService = new OrderService(orderRepository, outboxRepository, objectMapper);

        assertThrows(RuntimeException.class, () -> faultyService.getOrderById(orderId));
    }
}
