package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.model.ProcessedEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.example.orderservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

public class EventHandlerService {
    private static final Logger log = LoggerFactory.getLogger(EventHandlerService.class);
    private OrderRepository orderRepository;
    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;
    private ProcessedEventRepository processedEventRepository;

    public EventHandlerService(OrderRepository orderRepository, OutboxRepository outboxRepository,  ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.processedEventRepository = processedEventRepository;
        objectMapper=new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Transactional(rollbackFor = Exception.class)
    void handleEvent(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        String payload=rec.value();
        String topic=rec.topic();

        Order order=objectMapper.readValue(payload, Order.class);
        UUID orderId=order.getId();


        //idempotency check
        if(processedEventRepository.existsById(orderId)){
            return;
        }

        if(topic.equalsIgnoreCase("InventoryRejected") || topic.equalsIgnoreCase("PaymentRejected")){
            order.setStatus("CANCELLED");
            Outbox outbox=new Outbox();
            outbox.setId(UUID.randomUUID());
            outbox.setAggregateId(order.getId());
            outbox.setPayload(objectMapper.writeValueAsString(order));
            outbox.setType("OrderCancelled");
            outbox.setStatus("PENDING");
            outbox.setCreatedAt(LocalDateTime.now());
            log.info("order canceled wit id: {}", orderId);
            outboxRepository.save(outbox);
        }else if(topic.equalsIgnoreCase("PaymentAuthorized")){
            order.setStatus("COMPLETED");
            Outbox outbox=new Outbox();
            outbox.setId(UUID.randomUUID());
            outbox.setAggregateId(order.getId());
            outbox.setType("OrderCompleted");
            outbox.setPayload(objectMapper.writeValueAsString(order));
            outbox.setStatus("PENDING");
            outbox.setCreatedAt(LocalDateTime.now());
            log.info("order complted wit id: {}", orderId);
            outboxRepository.save(outbox);
        }

        processedEventRepository.save(new ProcessedEvent(orderId));
        orderRepository.save(order);
    }
}
