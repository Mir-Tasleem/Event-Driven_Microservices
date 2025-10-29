package com.example.notificationservice.service;

import com.example.notificationservice.dto.OrderFinal;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class NotificationHandlerService {
    private  ObjectMapper objectMapper=new ObjectMapper();
    private final NotificationRepository notificationRepository;
    private final Logger log= LoggerFactory.getLogger(this.getClass());

    public NotificationHandlerService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional(rollbackOn = Exception.class)
    public void handle(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        String jsonPayload=rec.value();
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
