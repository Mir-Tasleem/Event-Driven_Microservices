package com.example.notificationservice.service;

import com.example.notificationservice.config.KafkaConfigLoader;
import com.example.notificationservice.exception.ProducerNotInitialisedException;
import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Mock
    private NotificationHandlerService notificationHandlerService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private KafkaConfigLoader kafkaConfigLoader;

    @Mock
    private KafkaProducer<String, String> dlqProducer;

    @Mock
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> kafkaConsumer;

    @InjectMocks
    private NotificationService notificationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        notificationService = new NotificationService(kafkaConsumer, notificationHandlerService,dlqProducer);
    }


    @Test
    void testInitializeTransactions_CalledOnlyOnce() throws Exception {
        var method = NotificationService.class.getDeclaredMethod("initializeTransactions", KafkaProducer.class);
        method.setAccessible(true);

        doNothing().when(dlqProducer).initTransactions();

        method.invoke(notificationService, dlqProducer);
        method.invoke(notificationService, dlqProducer);

        verify(dlqProducer, times(1)).initTransactions();
    }

    @Test
    void testInitializeTransactionsIndirectly_Exception() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("OrderCompleted", 0, 0, "key", "value");

        doThrow(new RuntimeException("fail")).when(dlqProducer).initTransactions();

        when(dlqProducer.send(any())).thenReturn(mock(java.util.concurrent.Future.class));
        doNothing().when(dlqProducer).beginTransaction();
        doNothing().when(dlqProducer).commitTransaction();
        doNothing().when(dlqProducer).flush();
        doNothing().when(dlqProducer).close();

        Method sendToDLQMethod = NotificationService.class.getDeclaredMethod("sendToDLQ", ConsumerRecord.class, Exception.class);
        sendToDLQMethod.setAccessible(true);

        ProducerNotInitialisedException exception = assertThrows(
                ProducerNotInitialisedException.class,
                () -> {
                    try {
                        sendToDLQMethod.invoke(notificationService, rec, new RuntimeException("test"));
                    } catch (InvocationTargetException ite) {
                        throw ite.getCause();
                    }
                }
        );

        assertTrue(exception.getMessage().contains("Failed to initialize transactions"));

        verify(dlqProducer).initTransactions();
    }

    @Test
    void testProcessRecordWithRetry_SuccessFirstAttempt() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("OrderCompleted", 0, 0L, "key", "value");

        Method method = NotificationService.class.getDeclaredMethod("sendWithRetry", ConsumerRecord.class);
        method.setAccessible(true);

        method.invoke(notificationService, rec);

        verify(notificationHandlerService, times(1)).handle(rec);
        verify(kafkaConsumer, times(1)).commitAsync();
    }

    @Test
    void testSafelyAbortTransaction_Success() throws Exception {
        Method method = NotificationService.class.getDeclaredMethod("safelyAbortTransaction");
        method.setAccessible(true);

        method.invoke(notificationService);

        verify(dlqProducer, times(1)).abortTransaction();
    }

}
