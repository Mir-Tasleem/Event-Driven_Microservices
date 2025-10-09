package com.example.notificationservice.service;

import com.example.notificationservice.config.KafkaConfigLoader;
import com.example.notificationservice.dto.OrderFinal;
import com.example.notificationservice.dto.OrderItem;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private KafkaConfigLoader kafkaConfigLoader;

    @Mock
    private KafkaProducer<String, String> dlqProducer;

    @Mock
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> kafkaConsumer;

    @InjectMocks
    private NotificationService notificationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        notificationService = new NotificationService(kafkaConsumer, notificationRepository, kafkaConfigLoader);
    }

    private OrderItem createOrderItem(double price, int quantity) {
        return new OrderItem("SKU123",price,quantity);
    }

    private OrderFinal createOrderFinal(String status) {
        UUID orderId = UUID.randomUUID();
        String orderStatus = status;
        LocalDateTime createdAt = LocalDateTime.now();
        UUID customerId = UUID.randomUUID();
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(createOrderItem(100, 1));
        double totalAmount = orderItems.stream().mapToDouble(OrderItem::getPrice).sum();
        OrderFinal order = new OrderFinal(orderId, orderStatus, createdAt,customerId,orderItems, totalAmount);
        return order;
    }


    @Test
    void testHandle_SavesNotificationSuccessfully() throws Exception {
        // Arrange
        OrderFinal order = createOrderFinal("COMPLETED");
        String json = objectMapper.writeValueAsString(order);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("OrderCompleted", 0, 0, "key", json);

        // Act — invoke private handle method via reflection
        var method = NotificationService.class.getDeclaredMethod("handle", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(notificationService, record);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    // -------------------------------------------------
    // TEST 2: Initialize transactions only once
    // -------------------------------------------------
    @Test
    void testInitializeTransactions_CalledOnlyOnce() throws Exception {
        var method = NotificationService.class.getDeclaredMethod("initializeTransactions", KafkaProducer.class);
        method.setAccessible(true);

        doNothing().when(dlqProducer).initTransactions();

        // Call twice
        method.invoke(notificationService, dlqProducer);
        method.invoke(notificationService, dlqProducer);

        // Verify initTransactions called once
        verify(dlqProducer, times(1)).initTransactions();
    }


    @Test
    void testHandle_LogsNotificationMessage() throws Exception {
        OrderFinal order = createOrderFinal("CANCELLED");
        String json = objectMapper.writeValueAsString(order);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("OrderCancelled", 0, 0, "key", json);

        var method = NotificationService.class.getDeclaredMethod("handle", ConsumerRecord.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(notificationService, record));

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }
}
