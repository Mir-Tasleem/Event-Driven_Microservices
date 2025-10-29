package com.example.orderservice.service;

import com.example.orderservice.config.KafkaConfigLoader;
import com.example.orderservice.exception.ProducerNotInitialisedException;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.repository.OutboxRepository;

import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outboxRepository;
    private final KafkaProducer<String, String> kafkaProducer;
    private volatile boolean initialized = false;
    private final Object initLock = new Object();

    @Autowired
    public OutboxPublisher(OutboxRepository outboxRepository, KafkaConfigLoader kafkaConfigLoader) {
        this(outboxRepository, createKafkaProducer(kafkaConfigLoader));
    }

    // Package-private constructor for testing
    OutboxPublisher(OutboxRepository outboxRepository, KafkaProducer<String, String> kafkaProducer) {
        this.outboxRepository = outboxRepository;
        this.kafkaProducer = kafkaProducer;
    }

    private static KafkaProducer<String, String> createKafkaProducer(KafkaConfigLoader kafkaConfigLoader) {
        return new KafkaProducer<>(kafkaConfigLoader.getProducerProperties());
    }

    private void initializeTransactions() {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        this.kafkaProducer.initTransactions();
                        initialized = true;
                       log.info("Kafka transactions initialized successfully");
                    } catch (Exception e) {
                        throw new ProducerNotInitialisedException("Failed to initialize Kafka transactions", e);
                    }
                }
            }
        }
    }

    /**
     *
     * This method is used to publish the pending events
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
                rec.headers().add("eventId",UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                rec.headers().add("correlationId",event.getAggregateId().toString().getBytes(StandardCharsets.UTF_8));
                rec.headers().add("eventType",event.getType().getBytes(StandardCharsets.UTF_8));
                rec.headers().add("occurredAt",Instant.now().toString().getBytes(StandardCharsets.UTF_8));
                kafkaProducer.send(rec,((metadata, exception) -> {
                    if(exception != null){
                        log.error(exception.getMessage());
                    }else{
                        log.info("Event sent successfully");
                    }
                }));
            }

            for (Outbox event:events){
                event.setStatus("SENT");
                outboxRepository.save(event);
            }
            kafkaProducer.commitTransaction();
        }catch (Exception e){
            kafkaProducer.abortTransaction();
            log.error(e.getMessage());
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
