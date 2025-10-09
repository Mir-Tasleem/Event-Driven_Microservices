package com.example.paymentservice.service;

import com.example.paymentservice.config.KafkaConfigLoader;
import com.example.paymentservice.dto.InventoryOrderItem;
import com.example.paymentservice.dto.OrderRecieved;
import com.example.paymentservice.model.Outbox;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.ProcessedEvent;
import com.example.paymentservice.repository.OutboxRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PaymentServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private InventoryOrderItem createInventoryOrderItem(double price, int quantity) {
        return new InventoryOrderItem("SKU123", price, quantity);
    }
    private OrderRecieved createOrderRecieved(UUID orderId,InventoryOrderItem orderItems) {
        LocalDateTime createdAt = LocalDateTime.now();
        UUID customerId = UUID.randomUUID();
        double totalAmount = orderItems.getPrice() * orderItems.getQuantity();
        OrderRecieved order = new OrderRecieved(orderId, "PENDING",createdAt,customerId, List.of(orderItems),totalAmount);
        return order;
    }


    @Test
    void testHandlePayment_PaymentAuthorized() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderRecieved order = createOrderRecieved(orderId,createInventoryOrderItem(200.0, 10));

        String json = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("InventoryReserved", 0, 0L, "key", json);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);

        var method = PaymentService.class.getDeclaredMethod("handlePayment", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(paymentService, record);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertEquals("PaymentAuthorized", savedPayment.getStatus());
        assertEquals(orderId, savedPayment.getOrderId());
        assertEquals(2000, savedPayment.getAmount());

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox outbox = outboxCaptor.getValue();
        assertEquals("PaymentAuthorized", outbox.getType());
        assertEquals("PENDING", outbox.getStatus());
        assertTrue(outbox.getPayload().contains(orderId.toString()));

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }


    @Test
    void testHandlePayment_PaymentRejected() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderRecieved order = createOrderRecieved(orderId,createInventoryOrderItem(201.0, 5));

        String json = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("InventoryReserved", 0, 0L, "key", json);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);

        var method = PaymentService.class.getDeclaredMethod("handlePayment", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(paymentService, record);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals("PaymentRejected", captor.getValue().getStatus());
    }

    @Test
    void testHandlePayment_AlreadyProcessed_Skips() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderRecieved order = createOrderRecieved(orderId,createInventoryOrderItem(200.0, 10));

        String json = mapper.writeValueAsString(order);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("InventoryReserved", 0, 0L, "key", json);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(true);

        var method = PaymentService.class.getDeclaredMethod("handlePayment", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(paymentService, record);

        verify(paymentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }


    @Test
    void testDoPaymentLogic() throws Exception {
        UUID orderId = UUID.randomUUID();
        var evenOrder = createOrderRecieved(orderId, createInventoryOrderItem(200.0, 10)); // total = 2000.0
        var oddOrder = createOrderRecieved(orderId, createInventoryOrderItem(3.0, 1));    // total = 3.0

        var evenMethod = PaymentService.class.getDeclaredMethod("doPayment", OrderRecieved.class);
        evenMethod.setAccessible(true);

        boolean evenResult = (boolean) evenMethod.invoke(paymentService, evenOrder);
        boolean oddResult = (boolean) evenMethod.invoke(paymentService, oddOrder);

        assertTrue(evenResult);
        assertFalse(oddResult);
    }



    @Test
    void testInitializeTransactions_OnlyOnce() throws Exception {
        KafkaProducer<String, String> mockProducer = mock(KafkaProducer.class);
        doNothing().when(mockProducer).initTransactions();

        var method = PaymentService.class.getDeclaredMethod("initializeTransactions", KafkaProducer.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(paymentService, mockProducer));
        assertDoesNotThrow(() -> method.invoke(paymentService, mockProducer));

        verify(mockProducer, times(1)).initTransactions();
    }
}
