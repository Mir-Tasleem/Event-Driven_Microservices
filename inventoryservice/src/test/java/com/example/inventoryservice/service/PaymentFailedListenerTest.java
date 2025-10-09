package com.example.inventoryservice.service;

import com.example.inventoryservice.config.KafkaConfigLoader;
import com.example.inventoryservice.dto.PaymentFailedEvent;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentFailedListnerTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private KafkaConfigLoader configLoader;
    @Mock
    private KafkaConsumer<String, String> consumer;
    @Mock
    private KafkaProducer<String, String> producer;

    @InjectMocks
    private PaymentFailedListner listener;

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
    }

    @Test
    void testInitializeTransactions_OnlyOnce() throws Exception {
        KafkaProducer<String, String> mockProducer = mock(KafkaProducer.class);
        doNothing().when(mockProducer).initTransactions();

        var method = PaymentFailedListner.class.getDeclaredMethod("initializeTransactions", KafkaProducer.class);
        method.setAccessible(true);

        // Call twice
        method.invoke(listener, mockProducer);
        method.invoke(listener, mockProducer);

        // Should initialize only once
        verify(mockProducer, times(1)).initTransactions();
    }



    @Test
    void testHandlePaymentFailed_Success() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setId(eventId);
        event.setOrderId(orderId);

        Reservation reservation = new Reservation();
        reservation.setSku("SKU123");
        reservation.setQuantity(5L);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(10L);

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        String json = mapper.writeValueAsString(event);

        // Use reflection to call private method
        var method = PaymentFailedListner.class.getDeclaredMethod("handlePaymentFailed", String.class);
        method.setAccessible(true);

        // Act
        method.invoke(listener, json);

        // Assert
        assertEquals(15, stock.getAvailable());
        verify(stockRepository, times(1)).save(stock);
        verify(reservationRepository, times(1)).delete(reservation);
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    void testHandlePaymentFailed_Idempotent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setId(eventId);
        event.setOrderId(orderId);

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        String json = mapper.writeValueAsString(event);
        var method = PaymentFailedListner.class.getDeclaredMethod("handlePaymentFailed", String.class);
        method.setAccessible(true);

        // Act
        method.invoke(listener, json);

        // Assert — ensure no repo interactions after idempotency check
        verify(reservationRepository, never()).findByOrderId(any());
        verify(stockRepository, never()).save(any());
        verify(reservationRepository, never()).delete(any());
        verify(processedEventRepository, never()).save(any());
    }
}
