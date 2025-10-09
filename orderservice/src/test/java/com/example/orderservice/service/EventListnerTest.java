package com.example.orderservice.service;

import com.example.orderservice.config.KafkaConfigLoader;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.model.ProcessedEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.example.orderservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EventListnerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private KafkaConfigLoader configLoader;
    @Mock
    private KafkaConsumer<String, String> consumer;
    @Mock
    private KafkaProducer<String, String> producer;

    @InjectMocks
    private EventListner eventListner;

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
    }




    @Test
    void testHandleEvent_Idempotent_NoAction() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        String payload = mapper.writeValueAsString(order);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("InventoryRejected", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(true);

        eventListner.handleEvent(record);

        verify(outboxRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }




    @Test
    void testHandleEvent_InventoryRejected_CreatesCancelledOutbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("NEW");

        String payload = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("InventoryRejected", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(false);

        eventListner.handleEvent(record);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox savedOutbox = outboxCaptor.getValue();

        assertEquals("OrderCancelled", savedOutbox.getType());
        assertEquals("PENDING", savedOutbox.getStatus());
        assertTrue(savedOutbox.getPayload().contains(orderId.toString()));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals("CANCELLED", orderCaptor.getValue().getStatus());

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }




    @Test
    void testHandleEvent_PaymentAuthorized_CreatesCompletedOutbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("NEW");

        String payload = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("PaymentAuthorized", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(false);

        eventListner.handleEvent(record);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox savedOutbox = outboxCaptor.getValue();

        assertEquals("OrderCompleted", savedOutbox.getType());
        assertEquals("PENDING", savedOutbox.getStatus());
        assertTrue(savedOutbox.getPayload().contains(orderId.toString()));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals("COMPLETED", orderCaptor.getValue().getStatus());

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

}
