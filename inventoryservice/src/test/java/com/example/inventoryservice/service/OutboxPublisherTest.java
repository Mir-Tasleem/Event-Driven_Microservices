package com.example.inventoryservice.service;

import com.example.inventoryservice.exception.ProducerNotInitialisedException;
import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.repository.OutboxRepository;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaProducer<String, String> kafkaProducer;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private OutboxPublisher outboxPublisher;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable=MockitoAnnotations.openMocks(this);
        outboxPublisher = new OutboxPublisher(outboxRepository, kafkaProducer);
    }



    @Test
    void testPublishPending_InitializesTransactionsIndirectly() {
        Outbox mockOutbox = new Outbox();
        mockOutbox.setId(UUID.randomUUID());
        mockOutbox.setAggregateId(UUID.randomUUID());
        mockOutbox.setType("order-events");
        mockOutbox.setPayload("{\"orderId\":123}");
        mockOutbox.setStatus("PENDING");

        when(outboxRepository.findTop5ByStatus("PENDING"))
                .thenReturn(Collections.singletonList(mockOutbox));

        doNothing().when(kafkaProducer).initTransactions();
        doNothing().when(kafkaProducer).beginTransaction();
        doNothing().when(kafkaProducer).commitTransaction();
        doNothing().when(kafkaProducer).flush();

        outboxPublisher.publishPending();

        verify(kafkaProducer).initTransactions();
    }



    @Test
    void testPublishPending_InitializeTransactionsException() {
        doThrow(new RuntimeException("fail")).when(kafkaProducer).initTransactions();

        Outbox mockOutbox = new Outbox();
        mockOutbox.setId(UUID.randomUUID());
        mockOutbox.setAggregateId(UUID.randomUUID());
        mockOutbox.setType("order-events");
        mockOutbox.setPayload("{\"orderId\":123}");
        mockOutbox.setStatus("PENDING");

        when(outboxRepository.findTop5ByStatus("PENDING"))
                .thenReturn(Collections.singletonList(mockOutbox));

        ProducerNotInitialisedException exception = assertThrows(
                ProducerNotInitialisedException.class,
                () -> outboxPublisher.publishPending()
        );

        assertTrue(exception.getMessage().contains("Failed to initialize Kafka transactions"));

        verify(kafkaProducer).initTransactions();
    }


    @Test
    void testPublishPending_sendsEventsAndUpdatesStatus() {
        Outbox mockOutbox = new Outbox();
        mockOutbox.setId(UUID.randomUUID());
        mockOutbox.setAggregateId(UUID.randomUUID());
        mockOutbox.setType("order-events");
        mockOutbox.setPayload("{\"orderId\":123}");
        mockOutbox.setStatus("PENDING");
        mockOutbox.setCreatedAt(LocalDateTime.now());

        when(outboxRepository.findTop5ByStatus("PENDING")).thenReturn(Collections.singletonList(mockOutbox));

        doNothing().when(kafkaProducer).initTransactions();
        doNothing().when(kafkaProducer).beginTransaction();
        doNothing().when(kafkaProducer).commitTransaction();
        doNothing().when(kafkaProducer).flush();

        doAnswer(invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.onCompletion(mock(RecordMetadata.class), null);
            return null;
        }).when(kafkaProducer).send(any(), any());

        outboxPublisher.publishPending();

        verify(kafkaProducer).initTransactions();
        verify(kafkaProducer).beginTransaction();
        verify(kafkaProducer).commitTransaction();
        verify(kafkaProducer).flush();

        verify(kafkaProducer).send(recordCaptor.capture(), any());

        ProducerRecord<String, String> sentRecord = recordCaptor.getValue();
        assertEquals("order-events", sentRecord.topic());
        assertEquals("{\"orderId\":123}", sentRecord.value());

        assertNotNull(sentRecord.headers().lastHeader("eventId"));
        assertNotNull(sentRecord.headers().lastHeader("correlationId"));
        assertNotNull(sentRecord.headers().lastHeader("eventType"));
        assertNotNull(sentRecord.headers().lastHeader("occurredAt"));

        assertEquals("SENT", mockOutbox.getStatus());
        verify(outboxRepository).save(mockOutbox);
    }

    @Test
    void testPublishPending_handlesKafkaException() {
        when(outboxRepository.findTop5ByStatus("PENDING")).thenReturn(Collections.emptyList());

        doNothing().when(kafkaProducer).initTransactions();
        doNothing().when(kafkaProducer).beginTransaction();
        doThrow(new RuntimeException("Kafka failure")).when(kafkaProducer).commitTransaction();
        doNothing().when(kafkaProducer).abortTransaction();
        doNothing().when(kafkaProducer).flush();

        assertDoesNotThrow(() -> outboxPublisher.publishPending());

        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer).flush();
    }

    @Test
    void testCleanupClosesProducer() {
        outboxPublisher.cleanup();
        verify(kafkaProducer).close();
    }

    @AfterEach
    void close() throws Exception {
        closeable.close();
    }
}
