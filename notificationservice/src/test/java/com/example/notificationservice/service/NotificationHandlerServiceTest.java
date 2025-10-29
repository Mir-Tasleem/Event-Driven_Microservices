package com.example.notificationservice.service;

import com.example.notificationservice.dto.OrderFinal;
import com.example.notificationservice.dto.OrderItem;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationHandlerServiceTest {
    @Mock
    private NotificationRepository notificationRepository;

   private ObjectMapper mapper=new ObjectMapper();
   private NotificationHandlerService notificationHandlerService;
   private AutoCloseable closeable;

   @BeforeEach
    void setup() {
       closeable=MockitoAnnotations.openMocks(this);
       mapper.findAndRegisterModules();
       mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
       notificationHandlerService =new NotificationHandlerService(notificationRepository);
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
        return new OrderFinal(orderId, orderStatus, createdAt,customerId,orderItems, totalAmount);
    }


    @Test
    void testHandle_SavesNotificationSuccessfully() throws Exception {
        OrderFinal order = createOrderFinal("COMPLETED");
        String json = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("OrderCompleted", 0, 0, "key", json);

        notificationHandlerService.handle(rec);

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void testHandle_LogsNotificationMessage() throws Exception {
        OrderFinal order = createOrderFinal("CANCELLED");
        String json = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("OrderCancelled", 0, 0, "key", json);

        Logger mockLogger = mock(Logger.class);
        Field logField = NotificationHandlerService.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(notificationHandlerService, mockLogger);

        notificationHandlerService.handle(rec);
        verify(mockLogger, times(1))
                .info(contains("[Notification Sent] Your order with id:" +
                        order.getId() + " is OrderCancelled"));

        assertDoesNotThrow(() -> notificationHandlerService.handle(rec));
    }

    @AfterEach
    void close() throws Exception {
        closeable.close();
    }
}
