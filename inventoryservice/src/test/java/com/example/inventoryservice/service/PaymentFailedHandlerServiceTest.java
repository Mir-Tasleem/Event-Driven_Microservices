package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.PaymentFailedEvent;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.OutboxRepository;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaymentFailedHandlerServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockService stockService;
    @Mock private OrderProcessorService orderProcessorService;
    @Mock private ReservationRepository reservationRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private ProcessedEventRepository processedEventRepository;

    private ObjectMapper mapper;
    private PaymentFailedHandlerService paymentFailedHandlerService;
    private AutoCloseable closeable;

    private Reservation createReservation(UUID orderId){
        Reservation reservation = new Reservation();
        reservation.setOrderId(orderId);
        reservation.setSku("SKU123");
        reservation.setQuantity(5L);
        return reservation;
    }

    @BeforeEach
    void setUp(){
        closeable = MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        paymentFailedHandlerService = new PaymentFailedHandlerService( reservationRepository, stockRepository, processedEventRepository);
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void testHandlePaymentFailed_Success() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setId(eventId);
        event.setOrderId(orderId);

        String json = mapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        Reservation reservation = createReservation(orderId);

        Stock stock = new Stock();
        stock.setSku("SKU123");
        stock.setAvailable(10L);
        stock.setReserved(0L);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(stockRepository.findBySku("SKU123")).thenReturn(stock);

        paymentFailedHandlerService.handlePaymentFailed(json);

        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertEquals(15L, stockCaptor.getValue().getAvailable()); // 10 + 5 restored

        verify(reservationRepository).delete(reservation);

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertEquals(orderId, processedCaptor.getValue().getEventId());
    }

    @Test
    void testHandlePaymentFailed_AlreadyProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setId(eventId);
        event.setOrderId(UUID.randomUUID());

        String json = mapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        paymentFailedHandlerService.handlePaymentFailed(json);

        verifyNoInteractions(reservationRepository);
        verifyNoInteractions(stockRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

}
