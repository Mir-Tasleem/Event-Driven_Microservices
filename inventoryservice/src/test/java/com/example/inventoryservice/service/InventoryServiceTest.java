package com.example.inventoryservice.service;


import com.example.inventoryservice.exception.ProducerNotInitialisedException;
import com.example.inventoryservice.repository.*;
import com.example.inventoryservice.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    @Mock private StockService stockService;
    @Mock private OrderProcessorService orderProcessorService;
    @Mock private StockRepository stockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private KafkaConsumer<String, String> kafkaConsumer;
    @Mock private KafkaProducer<String, String> kafkaProducer;

    private InventoryService inventoryService;

    private ObjectMapper mapper=new ObjectMapper();
    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);  // Initialize mocks

        inventoryService = new InventoryService(
                orderProcessorService,
                kafkaConsumer,
                kafkaProducer
        );

        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }



    @Test
    void testInitializeTransactionsIndirectly_Success() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("OrderCreated", 0, 0, "key", "value");

        doNothing().when(kafkaProducer).initTransactions();
        doNothing().when(kafkaProducer).beginTransaction();
        doNothing().when(kafkaProducer).commitTransaction();
        doNothing().when(kafkaProducer).flush();
        doNothing().when(kafkaProducer).close();

        when(kafkaProducer.send(any())).thenReturn(mock(java.util.concurrent.Future.class));

        Method sendToDLQMethod = InventoryService.class.getDeclaredMethod("sendToDLQ", ConsumerRecord.class, Exception.class);
        sendToDLQMethod.setAccessible(true);
        sendToDLQMethod.invoke(inventoryService, rec, new RuntimeException("test"));

        verify(kafkaProducer).initTransactions();
    }


    @Test
    void testInitializeTransactionsIndirectly_Exception() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("OrderCreated", 0, 0, "key", "value");

        doThrow(new RuntimeException("fail")).when(kafkaProducer).initTransactions();

        when(kafkaProducer.send(any())).thenReturn(mock(java.util.concurrent.Future.class));
        doNothing().when(kafkaProducer).beginTransaction();
        doNothing().when(kafkaProducer).commitTransaction();
        doNothing().when(kafkaProducer).flush();
        doNothing().when(kafkaProducer).close();

        Method sendToDLQMethod = InventoryService.class.getDeclaredMethod("sendToDLQ", ConsumerRecord.class, Exception.class);
        sendToDLQMethod.setAccessible(true);

        ProducerNotInitialisedException exception = assertThrows(
                ProducerNotInitialisedException.class,
                () -> {
                    try {
                        sendToDLQMethod.invoke(inventoryService, rec, new RuntimeException("test"));
                    } catch (InvocationTargetException ite) {
                        throw ite.getCause();
                    }
                }
        );

        assertTrue(exception.getMessage().contains("Failed to initialize transactions"));

        verify(kafkaProducer).initTransactions();
    }


    @Test
    void testProcessRecordWithRetry_SuccessFirstAttempt() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("OrderCreated", 0, 0L, "key", "value");

        Method method = InventoryService.class.getDeclaredMethod("processRecordWithRetry", ConsumerRecord.class);
        method.setAccessible(true);

        method.invoke(inventoryService, rec);

        verify(orderProcessorService, times(1)).handleOrder(rec);
        verify(kafkaConsumer, times(1)).commitAsync();
    }

    @Test
    void testProcessRecordWithRetry_RetryThenSuccess() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("OrderCreated", 0, 0L, "key", "value");

        Method method = InventoryService.class.getDeclaredMethod("processRecordWithRetry", ConsumerRecord.class);
        method.setAccessible(true);

        doThrow(new RuntimeException("fail1"))
                .doThrow(new RuntimeException("fail2"))
                .doNothing()
                .when(orderProcessorService).handleOrder(rec);

        method.invoke(inventoryService, rec);

        verify(orderProcessorService, times(3)).handleOrder(rec);
        verify(kafkaConsumer, times(1)).commitAsync();
    }



    @Test
    void testSendToDLQ_SendsRecordWithErrorHeader() throws Exception {
        var rec = TestUtils.createConsumerRecord("OrderCreated", "{\"id\":\"123\"}");

        var e = new RuntimeException("test-error");

        var method = InventoryService.class.getDeclaredMethod("sendToDLQ", org.apache.kafka.clients.consumer.ConsumerRecord.class, Exception.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(inventoryService, rec, e));
    }

    @Test
    void testSafelyAbortTransaction_Success() throws Exception {
        Method method = InventoryService.class.getDeclaredMethod("safelyAbortTransaction");
        method.setAccessible(true);

        method.invoke(inventoryService);

        verify(kafkaProducer, times(1)).abortTransaction();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }
}
