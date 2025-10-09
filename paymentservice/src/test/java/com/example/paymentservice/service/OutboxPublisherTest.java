package com.example.paymentservice.service;

import com.example.paymentservice.config.KafkaConfigLoader;
import com.example.paymentservice.model.Outbox;
import com.example.paymentservice.repository.OutboxRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private KafkaProducer<String, String> kafkaProducer;
    @Mock
    private KafkaConfigLoader kafkaConfigLoader;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        outboxPublisher = new OutboxPublisher(outboxRepository, kafkaProducer);
    }

    private Outbox createOutbox(String status) {
        Outbox o = new Outbox();
        o.setId(UUID.randomUUID());
        o.setAggregateId(UUID.randomUUID());
        o.setType("PaymentAuthorized");
        o.setStatus(status);
        o.setPayload("{\"test\":\"data\"}");
        o.setCreatedAt(Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        return o;
    }

    @Test
    void testPublishPending_Success() {
        Outbox o1 = createOutbox("PENDING");
        Outbox o2 = createOutbox("PENDING");

        when(outboxRepository.findTop5ByStatus("PENDING")).thenReturn(List.of(o1, o2));

        doNothing().when(kafkaProducer).initTransactions();
        doNothing().when(kafkaProducer).beginTransaction();
        doNothing().when(kafkaProducer).commitTransaction();
        doNothing().when(kafkaProducer).flush();

        when(kafkaProducer.send(any(ProducerRecord.class), any())).thenReturn(mock(Future.class));

        outboxPublisher.publishPending();

        verify(outboxRepository, times(1)).findTop5ByStatus("PENDING");
        verify(kafkaProducer, times(1)).beginTransaction();
        verify(kafkaProducer, times(1)).commitTransaction();
        verify(kafkaProducer, times(1)).flush();

        assertEquals("SENT", o1.getStatus());
        assertEquals("SENT", o2.getStatus());
        verify(outboxRepository, times(2)).save(any(Outbox.class));
    }


    @Test
    void testPublishPending_WhenSendFails_AbortsTransaction() {
        Outbox o = createOutbox("PENDING");
        when(outboxRepository.findTop5ByStatus("PENDING")).thenReturn(List.of(o));

        doNothing().when(kafkaProducer).initTransactions();
        doNothing().when(kafkaProducer).beginTransaction();

        when(kafkaProducer.send(any(), any())).thenThrow(new RuntimeException("Kafka send failed"));

        doNothing().when(kafkaProducer).abortTransaction();
        doNothing().when(kafkaProducer).flush();

        assertDoesNotThrow(() -> outboxPublisher.publishPending());

        verify(kafkaProducer, times(1)).abortTransaction();
        verify(kafkaProducer, times(1)).flush();

        verify(outboxRepository, never()).save(o);
    }


    @Test
    void testInitializeTransactions_CalledOnlyOnce() throws Exception {
        var method = OutboxPublisher.class.getDeclaredMethod("initializeTransactions");
        method.setAccessible(true);

        doNothing().when(kafkaProducer).initTransactions();

        method.invoke(outboxPublisher);
        method.invoke(outboxPublisher);

        verify(kafkaProducer, times(1)).initTransactions();
    }


    @Test
    void testCleanup_ClosesProducer() {
        doNothing().when(kafkaProducer).close();
        outboxPublisher.cleanup();
        verify(kafkaProducer, times(1)).close();
    }
}
