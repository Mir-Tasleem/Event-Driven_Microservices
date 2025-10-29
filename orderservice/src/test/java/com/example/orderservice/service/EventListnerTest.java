package com.example.orderservice.service;

import com.example.orderservice.config.KafkaConfigLoader;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import static org.mockito.Mockito.*;

class EventListnerTest {

    @Mock
    private EventHandlerService eventHandlerService;
    @Mock
    private KafkaConfigLoader configLoader;
    @Mock
    private KafkaConsumer<String, String> consumer;
    @Mock
    private KafkaProducer<String, String> producer;

    private EventListner eventListner;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testHandleWithRetry_SuccessfulOnFirstAttempt() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("PaymentAuthorized", 0, 0, "key", "value");

        doNothing().when(eventHandlerService).handleEvent(rec);

        // use reflection to access private method
        var method = EventListner.class.getDeclaredMethod("handleWithRetry", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(eventListner, rec);

        verify(eventHandlerService, times(1)).handleEvent(rec);
    }

    @Test
    void testHandleWithRetry_FailsThenSucceeds() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("PaymentAuthorized", 0, 0, "key", "value");

        doThrow(new RuntimeException("fail"))
                .doThrow(new RuntimeException("fail again"))
                .doNothing()
                .when(eventHandlerService).handleEvent(rec);

        var method = EventListner.class.getDeclaredMethod("handleWithRetry", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(eventListner, rec);

        verify(eventHandlerService, times(3)).handleEvent(rec);
    }

    @Test
    void testHandleWithRetry_AllRetriesFail_SendsToDLQ() throws Exception {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("InventoryRejected", 0, 0, "key", "value");
        doThrow(new RuntimeException("fail")).when(eventHandlerService).handleEvent(rec);

        EventListner spyListener = spy(eventListner);
        doNothing().when(spyListener).sendToDLQ(any(), any());

        var method = EventListner.class.getDeclaredMethod("handleWithRetry", ConsumerRecord.class);
        method.setAccessible(true);
        method.invoke(spyListener, rec);

        verify(spyListener, times(1)).sendToDLQ(any(), any());
    }

}
