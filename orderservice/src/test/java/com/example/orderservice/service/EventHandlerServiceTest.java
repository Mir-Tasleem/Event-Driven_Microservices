package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.example.orderservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventHandlerServiceTest {
    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaConsumer<String, String> consumer;
    @Mock
    private KafkaProducer<String, String> producer;

    private AutoCloseable closeable;
    private ObjectMapper mapper=new ObjectMapper();
    private EventHandlerService eventHandlerService;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        eventHandlerService = new EventHandlerService(orderRepository, outboxRepository, processedEventRepository);
    }

    @Test
    void testHandleEvent_Idempotent_NoAction() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        String payload = mapper.writeValueAsString(order);

        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("OrderCancelled", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(true);

        eventHandlerService.handleEvent(rec);

        verify(outboxRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void testHandleEvent_InventoryRejected_CreatesCancelledOutbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("CREATED");
        String payload = mapper.writeValueAsString(order);

        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("InventoryRejected", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(false);

        eventHandlerService.handleEvent(rec);

        verify(outboxRepository, times(1)).save(any());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(processedEventRepository, times(1)).save(any());
    }

    @Test
    void testHandleEvent_PaymentRejected_CreatesCancelledOutbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("CREATED");
        String payload = mapper.writeValueAsString(order);

        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("PaymentRejected", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(false);

        eventHandlerService.handleEvent(rec);

        verify(outboxRepository, times(1)).save(any());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(processedEventRepository, times(1)).save(any());
    }

    @Test
    void testHandleEvent_PaymentAuthorized_CreatesCompletedOutbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("CREATED");
        String payload = mapper.writeValueAsString(order);

        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("PaymentAuthorized", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(false);

        eventHandlerService.handleEvent(rec);

        verify(outboxRepository, times(1)).save(any());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(processedEventRepository, times(1)).save(any());
    }

    @Test
    void testHandleEvent_UnknownTopic_NoOutboxCreated() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("CREATED");
        String payload = mapper.writeValueAsString(order);

        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("RandomTopic", 0, 0L, "key", payload);

        when(processedEventRepository.existsById(orderId)).thenReturn(false);

        eventHandlerService.handleEvent(rec);

        verify(outboxRepository, never()).save(any());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(processedEventRepository, times(1)).save(any());
    }


    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

}
