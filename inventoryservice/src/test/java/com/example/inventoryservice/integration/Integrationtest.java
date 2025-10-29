//package com.example.inventoryservice.integration;
//
//import com.example.inventoryservice.config.TestKafkaConfig;
//import com.example.inventoryservice.dto.InventoryOrderItem;
//import com.example.inventoryservice.dto.OrderCreated;
//import com.example.inventoryservice.model.*;
//import com.example.inventoryservice.repository.*;
//import com.example.inventoryservice.service.InventoryService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest(properties = "spring.profiles.active=test")
//@Import(TestKafkaConfig.class)
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//class InventoryServiceIntegrationTest {
//
//    @Autowired private InventoryService inventoryService;
//    @Autowired private StockRepository stockRepository;
//    @Autowired private ReservationRepository reservationRepository;
//    @Autowired private OutboxRepository outboxRepository;
//    @Autowired private ProcessedEventRepository processedEventRepository;
//
//    private ObjectMapper objectMapper = new ObjectMapper();
//
//    @BeforeEach
//    void setup() {
//        objectMapper.findAndRegisterModules();
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//    }
//
//    private OrderCreated createOrder(UUID orderId, int quantity) {
//        InventoryOrderItem item = new InventoryOrderItem("SKU123", 100.0, quantity);
//        return new OrderCreated(
//                orderId,
//                "PENDING",
//                LocalDateTime.now(),
//                UUID.randomUUID(),
//                List.of(item),
//                100.0 * quantity
//        );
//    }
//
//    @Test
//    @Transactional
//    void testHandleOrder_SuccessfulReservation() throws Exception {
//        // given
//        Stock stock = new Stock();
//        stock.setSku("SKU123");
//        stock.setAvailable(50L);
//        stock.setReserved(0L);
//        stockRepository.save(stock);
//
//        UUID orderId = UUID.randomUUID();
//        OrderCreated order = createOrder(orderId, 10);
//        String payload = objectMapper.writeValueAsString(order);
//
//        ConsumerRecord<String, String> record =
//                new ConsumerRecord<>("OrderCreated", 0, 0, orderId.toString(), payload);
//
//        // when
//        inventoryService.handleOrder(record);
//
//        // then
//        Outbox outbox = outboxRepository.findAll().get(0);
//        assertThat(outbox.getType()).isEqualTo("InventoryReserved");
//
//        Stock updatedStock = stockRepository.findBySku("SKU123");
//        assertThat(updatedStock.getAvailable()).isEqualTo(40L);
//        assertThat(updatedStock.getReserved()).isEqualTo(10L);
//
//        assertThat(processedEventRepository.findAll()).hasSize(1);
//        assertThat(reservationRepository.findAll()).hasSize(1);
//    }
//
//    @Test
//    @Transactional
//    void testHandleOrder_InventoryRejected() throws Exception {
//        // given
//        Stock stock = new Stock();
//        stock.setSku("SKU123");
//        stock.setAvailable(5L);
//        stock.setReserved(0L);
//        stockRepository.save(stock);
//
//        UUID orderId = UUID.randomUUID();
//        OrderCreated order = createOrder(orderId, 10);
//        String payload = objectMapper.writeValueAsString(order);
//
//        ConsumerRecord<String, String> record =
//                new ConsumerRecord<>("OrderCreated", 0, 0, orderId.toString(), payload);
//
//        // when
//        inventoryService.handleOrder(record);
//
//        // then
//        Outbox outbox = outboxRepository.findAll().get(0);
//        assertThat(outbox.getType()).isEqualTo("InventoryRejected");
//    }
//}
