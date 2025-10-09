package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryOrderItem;
import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.model.*;
import com.example.inventoryservice.repository.*;
import com.example.inventoryservice.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private KafkaConsumer<String, String> kafkaConsumer;
    @Mock private KafkaProducer<String, String> kafkaProducer;

    private InventoryService inventoryService;

    private ObjectMapper mapper=new ObjectMapper();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);  // Initialize mocks

        inventoryService = new InventoryService(
                stockRepository,
                kafkaConsumer,
                reservationRepository,
                outboxRepository,
                processedEventRepository,
                kafkaProducer
        );

        mapper = mapper;
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }




    private OrderCreated createOrder(UUID orderId){
        List<InventoryOrderItem> orderItems = new ArrayList<>();
        orderItems.add(new InventoryOrderItem("SKU123", 200.0,10));
        String status="PENDING";
        LocalDateTime  createdAt=LocalDateTime.now();
        UUID customerId=UUID.randomUUID();
        double totalAmount=2000.0;
        OrderCreated order = new OrderCreated(orderId, status, createdAt, customerId, orderItems, totalAmount);
        return order;
    }

    @Test
    void testReserveStock_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);
        
        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(20L);
        stock.setReserved(0L);

        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        var method = InventoryService.class.getDeclaredMethod("reserveStock", OrderCreated.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(inventoryService, order);

        assertTrue(result);
        assertEquals(10, stock.getAvailable());
        assertEquals(10, stock.getReserved());
        verify(stockRepository).save(stock);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void testReserveStock_InsufficientStock() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(1L);
        stock.setReserved(0L);

        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        var method = InventoryService.class.getDeclaredMethod("reserveStock", OrderCreated.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(inventoryService, order);

        assertFalse(result);
        verify(reservationRepository, never()).save(any());
        verify(stockRepository, never()).save(any());
    }


    @Test
    void testHandleOrder_AlreadyProcessed() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);


        when(processedEventRepository.existsByEventId(orderId)).thenReturn(true);

        String json = mapper.writeValueAsString(order);
        var record = TestUtils.createConsumerRecord("OrderCreated", json);

        inventoryService.handleOrder(record);

        verify(outboxRepository, never()).save(any());
        verify(stockRepository, never()).save(any());
    }

    @Test
    void testHandleOrder_ReservationRejected() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        String json = mapper.writeValueAsString(order);
        var record = TestUtils.createConsumerRecord("OrderCreated", json);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);

        Stock stock = new Stock();
        stock.setSku("SKU1");
        stock.setAvailable(5L);
        stock.setReserved(0L);
        when(stockRepository.findBySku("SKU1")).thenReturn(stock);

        inventoryService.handleOrder(record);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("InventoryRejected", outboxCaptor.getValue().getType());
    }

    @Test
    void testHandleOrder_SuccessfulReservation() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        String json = mapper.writeValueAsString(order);

        var record = TestUtils.createConsumerRecord("OrderCreated", json);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(20L);
        stock.setReserved(0L);
        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        inventoryService.handleOrder(record);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        Outbox saved = outboxCaptor.getValue();

        assertEquals("InventoryReserved", saved.getType());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void testSendToDLQ_SendsRecordWithErrorHeader() throws Exception {
        var record = TestUtils.createConsumerRecord("OrderCreated", "{\"id\":\"123\"}");

        var e = new RuntimeException("test-error");

        var method = InventoryService.class.getDeclaredMethod("sendToDLQ", org.apache.kafka.clients.consumer.ConsumerRecord.class, Exception.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(inventoryService, record, e));
    }

}
