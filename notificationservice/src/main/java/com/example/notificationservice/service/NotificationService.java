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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.protocol.types.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private NotificationRepository notificationRepository;
    private KafkaConfigLoader configLoader;

    KafkaConsumer<String, String> kafkaConsumer;
    ObjectMapper objectMapper=new ObjectMapper();

    public NotificationService(NotificationRepository notificationRepository, KafkaConfigLoader configLoader){
        this.notificationRepository=notificationRepository;
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        Properties props=configLoader.getConsumerProperties();
        this.kafkaConsumer=new KafkaConsumer<>(props);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumerThread() {
        Thread thread = new Thread(() -> {
            try {
                sendNotification();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(false);
        thread.start();
    }

    public void sendNotification(){
        kafkaConsumer.subscribe(List.of("OrderCompleted","OrderCancelled"));
        while(true){
            ConsumerRecords<String, String> recs=kafkaConsumer.poll(Duration.ofMillis(100));
            for(ConsumerRecord<String, String> rec:recs) {
                try {
                    handle(rec);
                    kafkaConsumer.commitAsync();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }


    @Transactional
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
}
