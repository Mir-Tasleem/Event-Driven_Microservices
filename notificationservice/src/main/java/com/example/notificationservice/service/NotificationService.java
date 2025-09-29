package com.example.notificationservice.service;

import com.example.notificationservice.config.KafkaConfigLoader;
import com.example.notificationservice.dto.OrderFinal;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private KafkaConfigLoader configLoader;

    KafkaConsumer<String, String> kafkaConsumer=new KafkaConsumer<>(configLoader.getConsumerProperties());
    KafkaProducer<String, String> kafkaProducer=new KafkaProducer<>(configLoader.getProducerProperties());
    ObjectMapper objectMapper=new ObjectMapper();

    public void sendNotification(){
        kafkaConsumer.subscribe(List.of("OrderCreated","OrderCancelled"));
        ConsumerRecords<String, String> recs=kafkaConsumer.poll(Duration.ofMillis(100));
        try{
            for(ConsumerRecord<String, String> rec:recs){
                try{
                    handle(rec);
                    kafkaConsumer.commitAsync();
                }catch (Exception e){
                    sendToDLQ(rec,e);
                }
            }
            kafkaProducer.commitTransaction();
        }catch (Exception e){
            kafkaProducer.abortTransaction();
        }finally {
            kafkaProducer.flush();
            kafkaProducer.close();
            kafkaConsumer.close();
        }
    }

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

        kafkaProducer.send(new ProducerRecord<>("Notification",notification.toString()));
    }

    private void sendToDLQ(ConsumerRecord<String, String> record, Exception e){
        KafkaProducer<String, String> dlqProducer = new KafkaProducer<>(configLoader.getProducerProperties());
        ProducerRecord<String, String> rec = new ProducerRecord<>("InventoryDLQ", record.value());
        rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
        dlqProducer.send(rec);
    }
}
