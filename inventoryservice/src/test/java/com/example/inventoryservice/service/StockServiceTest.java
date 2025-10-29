package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryOrderItem;
import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private StockService stockService;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp(){
        closeable = MockitoAnnotations.openMocks(this);  // Initialize mocks
        stockService=new StockService(stockRepository,reservationRepository);
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
    void testReserveStock_InsufficientStock(){
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(1L);
        stock.setReserved(0L);

        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        boolean result = stockService.reserveStock(order);

        assertFalse(result);
        verify(reservationRepository, never()).save(any());
        verify(stockRepository, never()).save(any());
    }

    @Test
    void testReserveStock_Success(){
        UUID orderId = UUID.randomUUID();
        OrderCreated order = createOrder(orderId);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(20L);
        stock.setReserved(0L);

        when(stockRepository.findBySku("SKU123")).thenReturn(stock);


        boolean result = stockService.reserveStock(order);

        assertTrue(result);
        assertEquals(10, stock.getAvailable());
        assertEquals(10, stock.getReserved());
        verify(stockRepository).save(stock);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }
}
