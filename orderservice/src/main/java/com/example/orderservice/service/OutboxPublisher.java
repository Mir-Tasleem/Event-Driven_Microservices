package com.example.orderservice.service;

import com.example.orderservice.model.Outbox;
import com.example.orderservice.model.ProcessedEvent;
import com.example.orderservice.repository.OutboxRepository;
import com.example.orderservice.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxPublisher {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishPending() {
        List<Outbox> events = outboxRepository.findTop5ByStatus("PENDING");

        for(Outbox event : events) {
            var message= MessageBuilder.withPayload(event.getPayload())
                                    .setHeader("eventId", UUID.randomUUID().toString())
                                    .setHeader("correlationId",event.getAggregateId().toString())
                                    .setHeader("eventType",event.getType())
                                    .setHeader("occurredAt", Instant.now().toString())
                                    .setHeader("producer","order-svc")
                                    .build();
            kafkaTemplate.send("orders.created", String.valueOf(message));
            event.setStatus("SENT");
            outboxRepository.save(event);
        }
    }
}
