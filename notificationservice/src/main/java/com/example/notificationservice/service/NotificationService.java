package com.example.notificationservice.service;

import com.example.notificationservice.config.KafkaConfigLoader;
import com.example.notificationservice.dto.OrderFinal;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
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
import java.util.Properties;
import java.util.UUID;

@Service
public class NotificationService {
    private boolean initialized = false;
    private final Object initLock = new Object();
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private NotificationRepository notificationRepository;
    private KafkaConfigLoader configLoader;

    KafkaConsumer<String, String> kafkaConsumer;
    ObjectMapper objectMapper=new ObjectMapper();

    public NotificationService(KafkaConsumer<String, String> kafkaConsumer,NotificationRepository notificationRepository, KafkaConfigLoader configLoader){
        this.notificationRepository=notificationRepository;
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
                int maxRetries=3;
                int attempt=0;
                long backoffMillis = 2000;
                boolean success=false;
                while(attempt<maxRetries && !success){
                    try{
                        handle(rec);
                        success=true;
                    }catch (Exception e){
                        attempt++;
                        if(attempt<maxRetries){
                            try {
                                Thread.sleep(backoffMillis);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }else{
                            sendToDLQ(rec, e);
                        }
                    }
                }
            }
        }
    }


    @Transactional(rollbackOn = Exception.class)
    private void handle(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        String jsonPayload=rec.value();
        Headers headers=rec.headers();
        String topic=rec.topic();

        OrderFinal orderFinal=objectMapper.readValue(jsonPayload, OrderFinal.class);

        //create Notification
        Notification notification=new Notification();
        notification.setId(UUID.randomUUID());
        notification.setOrderId(orderFinal.getId());
        notification.setStatus(orderFinal.getStatus());
        notification.setCreatedAt(orderFinal.getCreatedAt());
        notification.setChannel("channel");
        notification.setPayload(orderFinal.toString());
        notificationRepository.save(notification);

        String notificationMessage= "[Notification Sent] Your order with id:"+orderFinal.getId().toString()+" is "+topic;
        log.info(notificationMessage);
    }

    private void initializeTransactions(KafkaProducer<String, String> producer) {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        producer.initTransactions();
                        initialized = true;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize transactions", e);
                    }
                }
            }
        }
    }

    private void sendToDLQ(ConsumerRecord<String, String> record, Exception e){
        Properties dlqprops=configLoader.getProducerProperties();
        dlqprops.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,"notification-dlq-tx");
        KafkaProducer<String, String> dlqProducer = new KafkaProducer<>(dlqprops);
        initializeTransactions(dlqProducer);
        try {
            dlqProducer.beginTransaction();
            ProducerRecord<String, String> rec = new ProducerRecord<>("PaymentDLQ", record.value());
            rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
            dlqProducer.send(rec).get();
            dlqProducer.commitTransaction();
        }catch (Exception ex){
            try {
                dlqProducer.abortTransaction();
            } catch (Exception abortEx) {
                System.err.println("Failed to abort transaction: " + abortEx.getMessage());
            }
            System.err.println("Failed to send to DLQ: " + ex.getMessage());
        }
        dlqProducer.close();
    }
}
