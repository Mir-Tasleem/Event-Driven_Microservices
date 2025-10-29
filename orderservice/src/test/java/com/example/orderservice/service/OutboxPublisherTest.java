package com.example.orderservice.service;

import com.example.orderservice.model.Outbox;
import com.example.orderservice.repository.OutboxRepository;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaProducer<String, String> kafkaProducer;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        outboxPublisher = new OutboxPublisher(outboxRepository, kafkaProducer);
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
}
