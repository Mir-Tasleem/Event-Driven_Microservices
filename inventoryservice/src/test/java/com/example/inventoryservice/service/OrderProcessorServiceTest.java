package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryOrderItem;
import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.OutboxRepository;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.example.inventoryservice.repository.StockRepository;
import com.example.inventoryservice.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderProcessorServiceTest {

    @Mock
    StockService stockService;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    StockRepository stockRepository;

    private OrderProcessorService orderProcessorService;
    private ObjectMapper mapper=new ObjectMapper();
    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);  // Initialize mocks

        orderProcessorService=new OrderProcessorService(stockService,outboxRepository,processedEventRepository);

        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private OrderCreated createOrder(UUID orderId){
        List<InventoryOrderItem> orderItems = new ArrayList<>();
        orderItems.add(new InventoryOrderItem("SKU123", 200.0,10));
        String status="PENDING";
        LocalDateTime createdAt=LocalDateTime.now();
        UUID customerId=UUID.randomUUID();
        double totalAmount=2000.0;
        return new OrderCreated(orderId, status, createdAt, customerId, orderItems, totalAmount);
    }

    @Test
    void testHandleOrder_AlreadyProcessed() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);


        when(processedEventRepository.existsByEventId(orderId)).thenReturn(true);

        String json = mapper.writeValueAsString(order);
        var rec = TestUtils.createConsumerRecord("OrderCreated", json);

        orderProcessorService.handleOrder(rec);

        verify(outboxRepository, never()).save(any());
        verify(stockRepository, never()).save(any());
    }

    @Test
    void testHandleOrder_ReservationRejected() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        String json = mapper.writeValueAsString(order);
        var rec = TestUtils.createConsumerRecord("OrderCreated", json);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(5L);
        stock.setReserved(0L);
        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);
        when(stockService.reserveStock(any(OrderCreated.class))).thenReturn(false);


        orderProcessorService.handleOrder(rec);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("InventoryRejected", outboxCaptor.getValue().getType());
    }

    @Test
    void testHandleOrder_SuccessfulReservation() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        String json = mapper.writeValueAsString(order);

        var rec = TestUtils.createConsumerRecord("OrderCreated", json);


        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(20L);
        stock.setReserved(0L);
        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);
        when(stockService.reserveStock(any(OrderCreated.class))).thenReturn(true);


        orderProcessorService.handleOrder(rec);

        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        assertEquals("InventoryReserved", outboxCaptor.getValue().getType());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(stockService).reserveStock(any(OrderCreated.class));
    }

    @AfterEach
    void close() throws Exception {
        closeable.close();
    }
}
