package com.example.inventoryservice.service;


import com.example.inventoryservice.config.KafkaConfigLoader;
import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.model.ProcessedEvent;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class OutboxPublisher {
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private KafkaProducer<String, String> kafkaProducer=new KafkaProducer<>(KafkaConfigLoader.getProducerProperties());


    private void initializeTransactions() {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        this.kafkaProducer.initTransactions();
                        initialized = true;
                        System.out.println("Kafka transactions initialized successfully");
                    } catch (Exception e) {
                        System.err.println("Failed to initialize Kafka transactions: " + e.getMessage());
                        throw new RuntimeException("Failed to initialize Kafka transactions", e);
                    }
                }
            }
        }
    }


    /**
     * Publishes the top 5 pending events in the Outbox.
     *
     * This method is annotated with @Scheduled and @Transactional, meaning it will be executed every 5 seconds and will be wrapped in a database transaction.
     *
     * It will retrieve the top 5 pending events from the Outbox, publish them to Kafka and update their status to "SENT".
     *
     * If an exception occurs during the publishing of an event, it will be logged and the transaction will be rolled back.
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void publishPending() {
        List<Outbox> events = outboxRepository.findTop5ByStatus("PENDING");
        initializeTransactions();
        kafkaProducer.beginTransaction();
        try{
            for(Outbox event : events) {
                ProducerRecord<String, String> rec = new ProducerRecord<>(event.getType(), event.getPayload());
                rec.headers().add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                rec.headers().add("correlationId", event.getAggregateId().toString().getBytes(StandardCharsets.UTF_8));
                rec.headers().add("eventType", event.getType().getBytes(StandardCharsets.UTF_8));
                rec.headers().add("occurredAt", Instant.now().toString().getBytes(StandardCharsets.UTF_8));

                kafkaProducer.send(rec, ((metadata, exception) -> {
                    if (exception != null) {
                        System.out.println(exception.getMessage());
                    } else {
                        System.out.println("Event sent successfully");
                    }
                }));
            }

            kafkaProducer.commitTransaction();

            for(Outbox event : events) {
                event.setStatus("SENT");
                outboxRepository.save(event);
            }
        }catch (Exception e){
            kafkaProducer.abortTransaction();
            System.out.println(e.getMessage());
        }finally {
            kafkaProducer.flush();
        }

    }

    @PreDestroy
    public void cleanup() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
    }
}
