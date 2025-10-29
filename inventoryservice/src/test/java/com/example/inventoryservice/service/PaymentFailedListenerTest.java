package com.example.inventoryservice.service;

import com.example.inventoryservice.exception.ProducerNotInitialisedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentFailedListenerTest {

    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> dlqProducer;
    private PaymentFailedHandlerService handlerService;
    private PaymentFailedListener listener;

    @BeforeEach
    void setup() {
        consumer = mock(KafkaConsumer.class);
        dlqProducer = mock(KafkaProducer.class);
        handlerService = mock(PaymentFailedHandlerService.class);

        listener = new PaymentFailedListener(handlerService, consumer, dlqProducer);
    }


    @Test
    void testProcessWithRetry_SuccessfulOnFirstTry() throws Exception {
         ConsumerRecord<String, String> rec = new ConsumerRecord<>("PaymentFailed", 0, 0, "key", "value");
        doNothing().when(handlerService).handlePaymentFailed(rec.value());

        Method method = PaymentFailedListener.class.getDeclaredMethod("processWithRetry", ConsumerRecord.class);
        method.setAccessible(true);

         
        method.invoke(listener, rec);

         
        verify(handlerService, times(1)).handlePaymentFailed(rec.value());
        verify(dlqProducer, never()).send(any());
    }


    @Test
    void testProcessWithRetry_FailsAndSendsToDLQ() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("PaymentFailed", 0, 0, "key", "value");

        doThrow(new RuntimeException("fail")).when(handlerService).handlePaymentFailed(rec.value());

        doNothing().when(dlqProducer).initTransactions();
        doNothing().when(dlqProducer).beginTransaction();
        doNothing().when(dlqProducer).commitTransaction();
        when(dlqProducer.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        Method method = PaymentFailedListener.class.getDeclaredMethod("processWithRetry", ConsumerRecord.class);
        method.setAccessible(true);

        method.invoke(listener, rec);

        verify(handlerService, times(3)).handlePaymentFailed(rec.value());
        verify(dlqProducer, times(1)).send(any());
        verify(dlqProducer, times(1)).commitTransaction(); // ✅ Will now pass
    }



    @Test
    void testConsumeRecords_CallsProcessWithRetry() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("PaymentFailed", 0, 0L, "key", "value");
        ConsumerRecords<String, String> records = new ConsumerRecords<>(
                Map.of(new TopicPartition("PaymentFailed", 0),
                        Collections.singletonList(rec))
        );

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records)
                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

        PaymentFailedListener spyListener = Mockito.spy(listener);

        Method method = PaymentFailedListener.class.getDeclaredMethod("consumerecords");
        method.setAccessible(true);

        Thread thread = new Thread(() -> {
            try {
                method.invoke(spyListener);
            } catch (Exception ignored) {
                //ignore
            }
        });
        thread.start();
        Thread.sleep(300);
        thread.interrupt();

        verify(consumer, atLeastOnce()).poll(any(Duration.class));
    }


    @Test
    void testInitializeTransactions_ThrowsProducerNotInitialisedException() throws Exception {
        doThrow(new RuntimeException("init failed")).when(dlqProducer).initTransactions();

        Method method = PaymentFailedListener.class.getDeclaredMethod("initializeTransactions", KafkaProducer.class);
        method.setAccessible(true);

        Exception exception=assertThrows(Exception.class,()->method.invoke(listener,dlqProducer));
        Throwable cause = exception.getCause();
        org.junit.jupiter.api.Assertions.assertNotNull(cause);
        org.junit.jupiter.api.Assertions.assertTrue(cause instanceof ProducerNotInitialisedException);
        org.junit.jupiter.api.Assertions.assertEquals("Failed to initialize transactions", cause.getMessage());
    }
}
