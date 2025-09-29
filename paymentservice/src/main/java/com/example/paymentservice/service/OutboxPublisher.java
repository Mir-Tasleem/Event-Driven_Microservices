package com.example.paymentservice.service;

import com.example.inventoryservice.config.KafkaConfigLoader;
import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.repository.OutboxRepository;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxPublisher {
    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private KafkaProducer<String, String> kafkaProducer=new KafkaProducer<>(KafkaConfigLoader.getProducerProperties());

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishPending() {
        List<Outbox> events = outboxRepository.findTop5ByStatus("PENDING");

        for(Outbox event : events) {
            ProducerRecord<String, String> rec = new ProducerRecord<>(event.getType(), event.getPayload());
            rec.headers().add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            rec.headers().add("correlationId",event.getAggregateId().toString().getBytes(StandardCharsets.UTF_8));
            rec.headers().add("eventType",event.getType().getBytes(StandardCharsets.UTF_8));
            rec.headers().add("occurredAt", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            kafkaProducer.send(rec,((metadata, exception) -> {
                if(exception != null){
                    System.out.println(exception.getMessage());
                }else{
                    System.out.println("Event sent successfully");
                }
            }));
            event.setStatus("SENT");
            outboxRepository.save(event);
        }
        kafkaProducer.flush();
        kafkaProducer.close();
    }
}
