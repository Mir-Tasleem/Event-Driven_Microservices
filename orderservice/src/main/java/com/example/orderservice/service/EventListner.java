package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.model.ProcessedEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.example.orderservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class EventListner {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;
    private ObjectMapper objectMapper=new ObjectMapper();

    @KafkaListener(topics = {"inventory.reserved","inventory.rejected","payment.authroized","payment.rejected"}, groupId = "order-svc")
    public void handle(String payload, @Header("eventId") String eventId, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws JsonProcessingException {
        //idempotency check
        if(processedEventRepository.existsById(UUID.fromString(eventId))){
            return;
        }

        String orderId=objectMapper.readTree(payload).get("orderId").asText();
        Optional<Order>  optOrder = orderRepository.findById(UUID.fromString(orderId));
        Order order=optOrder.get();

        if(topic.equalsIgnoreCase("inventory.rejected") || topic.equalsIgnoreCase("payment.rejected")){
            order.setStatus("REJECTED");
            Outbox outbox=new Outbox();
            outbox.setId(UUID.randomUUID());
            outbox.setAggregateId(order.getId());
            outbox.setType("OrderCancelled");
            outboxRepository.save(outbox);
        }else if(topic.equalsIgnoreCase("payment.authroized")){
            order.setStatus("COMPLETED");
            Outbox outbox=new Outbox();
            outbox.setId(UUID.randomUUID());
            outbox.setAggregateId(order.getId());
            outbox.setType("OrderCompleted");
            outboxRepository.save(outbox);
        }

        processedEventRepository.save(new ProcessedEvent(UUID.fromString(eventId)));
    }
}
