package com.example.notificationservice.service;

import com.example.notificationservice.exception.ProducerNotInitialisedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class NotificationService {
    private boolean initialized = false;
    private final Object initLock = new Object();
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private KafkaProducer<String, String> dlqProducer;
    private final NotificationHandlerService notificationHandlerService;

    KafkaConsumer<String, String> kafkaConsumer;
    ObjectMapper objectMapper=new ObjectMapper();

    public NotificationService(KafkaConsumer<String, String> kafkaConsumer,
                               NotificationHandlerService notificationHandlerService,
                               KafkaProducer<String, String> dlqProducer){
        this.notificationHandlerService = notificationHandlerService;
        this.dlqProducer=dlqProducer;
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.kafkaConsumer=kafkaConsumer;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void startConsumerThread() {
        try {
            sendNotification();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendNotification(){
        kafkaConsumer.subscribe(List.of("OrderCompleted","OrderCancelled"));
        while(true){
            ConsumerRecords<String, String> recs=kafkaConsumer.poll(Duration.ofMillis(100));
            for(ConsumerRecord<String, String> rec:recs) {
               sendWithRetry(rec);
            }
        }
    }

    private void sendWithRetry(ConsumerRecord<String, String> rec){
        int maxRetries = 3;
        long backoffMillis = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                notificationHandlerService.handle(rec);
                kafkaConsumer.commitAsync();
                return; // success
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    sleep(backoffMillis);
                } else {
                    sendToDLQ(rec, e);
                }
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void initializeTransactions(KafkaProducer<String, String> producer) {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        producer.initTransactions();
                        initialized = true;
                    } catch (Exception e) {
                        throw new ProducerNotInitialisedException("Failed to initialize transactions", e);
                    }
                }
            }
        }
    }

    private void sendToDLQ(ConsumerRecord<String, String> consumerRecord, Exception e){
        initializeTransactions(dlqProducer);
        try {
            dlqProducer.beginTransaction();
            ProducerRecord<String, String> rec = new ProducerRecord<>("PaymentDLQ", consumerRecord.value());
            rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
            dlqProducer.send(rec).get();
            dlqProducer.commitTransaction();
        }catch (InterruptedException ie){
            Thread.currentThread().interrupt();
            log.error("Thread was interrupted while sending to DLQ", ie);
            safelyAbortTransaction();
        }
        catch (Exception ex){
           safelyAbortTransaction();
           log.error("Failed to send to DLQ: {}" , ex.getMessage());
        }
        dlqProducer.close();
    }

    private void safelyAbortTransaction() {
        try {
            dlqProducer.abortTransaction();
        } catch (Exception abortEx) {
            log.error("Failed to abort DLQ transaction: {}", abortEx.getMessage(), abortEx);
        }
    }
}
