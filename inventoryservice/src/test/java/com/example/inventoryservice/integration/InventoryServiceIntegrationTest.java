//package com.example.inventoryservice.integration;
//
//import com.example.inventoryservice.dto.InventoryOrderItem;
//import com.example.inventoryservice.dto.OrderCreated;
//import com.example.inventoryservice.model.*;
//import com.example.inventoryservice.repository.*;
//import com.example.inventoryservice.service.InventoryService;
//import com.example.inventoryservice.service.OrderProcessorService;
//import com.example.inventoryservice.service.StockService;
//import com.example.inventoryservice.util.TestUtils;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import org.apache.kafka.clients.consumer.KafkaConsumer;
//import org.apache.kafka.clients.producer.KafkaProducer;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.*;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//import static org.mockito.Mockito.*;
//import static org.junit.jupiter.api.Assertions.*;
//
//class InventoryServiceIntegrationTest {
//
//    @Mock private StockRepository stockRepository;
//    @Mock private StockService stockService;
//    @Mock private OrderProcessorService orderProcessorService;
//    @Mock private ReservationRepository reservationRepository;
//    @Mock private OutboxRepository outboxRepository;
//    @Mock private ProcessedEventRepository processedEventRepository;
//    @Mock private KafkaConsumer<String, String> kafkaConsumer;
//    @Mock private KafkaProducer<String, String> kafkaProducer;
//
//    private InventoryService inventoryService;
//
//    private ObjectMapper mapper=new ObjectMapper();
//
//    @BeforeEach
//    void setup() {
//        MockitoAnnotations.openMocks(this);  // Initialize mocks
//
//        inventoryService = new InventoryService(
//                stockService,
//                orderProcessorService,
//                kafkaConsumer,
//                outboxRepository,
//                processedEventRepository,
//                kafkaProducer
//        );
//
//
//        mapper.findAndRegisterModules();
//        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//    }
//
//
//
//
//    private OrderCreated createOrder(UUID orderId){
//        List<InventoryOrderItem> orderItems = new ArrayList<>();
//        orderItems.add(new InventoryOrderItem("SKU123", 200.0,10));
//        String status="PENDING";
//        LocalDateTime  createdAt=LocalDateTime.now();
//        UUID customerId=UUID.randomUUID();
//        double totalAmount=2000.0;
//        OrderCreated order = new OrderCreated(orderId, status, createdAt, customerId, orderItems, totalAmount);
//        return order;
//    }
//
//
//    @Test
//    void testHandleOrder_AlreadyProcessed() throws Exception {
//        UUID orderId = UUID.randomUUID();
//        OrderCreated order = createOrder(orderId);
//
//
//        when(processedEventRepository.existsByEventId(orderId)).thenReturn(true);
//
//        String json = mapper.writeValueAsString(order);
//        var rec = TestUtils.createConsumerRecord("OrderCreated", json);
//
//        orderProcessorService.handleOrder(rec);
//
//        verify(outboxRepository, never()).save(any());
//        verify(stockRepository, never()).save(any());
//        verify(reservationRepository, never()).save(any());
//    }
//
//    @Test
//    void testHandleOrder_ReservationRejected() throws Exception {
//        UUID orderId = UUID.randomUUID();
//        OrderCreated order = createOrder(orderId);
//
//        String json = mapper.writeValueAsString(order);
//        var record = TestUtils.createConsumerRecord("OrderCreated", json);
//
//        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);
//
//        Stock stock = new Stock();
//        stock.setSku("SKU123");
//        stock.setAvailable(5L);
//        stock.setReserved(0L);
//        when(stockRepository.findBySku("SKU123")).thenReturn(stock);
//
//        inventoryService.handleOrder(record);
//
//        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
//        verify(outboxRepository).save(outboxCaptor.capture());
//        assertEquals("InventoryRejected", outboxCaptor.getValue().getType());
//
//        verify(processedEventRepository).save(any(ProcessedEvent.class));
//
//        verify(reservationRepository,never()).save(any());
//    }
//
//    @Test
//    void testHandleOrder_SuccessfulReservation() throws Exception {
//        UUID orderId = UUID.randomUUID();
//        OrderCreated order = createOrder(orderId);
//
//        String json = mapper.writeValueAsString(order);
//
//        var record = TestUtils.createConsumerRecord("OrderCreated", json);
//
//        when(processedEventRepository.existsByEventId(orderId)).thenReturn(false);
//
//        Stock stock = new Stock();
//        stock.setSku("SKU123");
//        stock.setAvailable(20L);
//        stock.setReserved(0L);
//        when(stockRepository.findBySku("SKU123")).thenReturn(stock);
//
//        inventoryService.handleOrder(record);
//
//        verify(reservationRepository).save(any(Reservation.class));
//
//        ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
//        verify(outboxRepository).save(outboxCaptor.capture());
//        Outbox saved = outboxCaptor.getValue();
//        assertEquals("InventoryReserved", saved.getType());
//
//        verify(processedEventRepository).save(any(ProcessedEvent.class));
//    }
//}
