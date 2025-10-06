package com.example.orderservice.service;

import com.example.orderservice.config.KafkaConfigLoader;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.Outbox;
import com.example.orderservice.model.ProcessedEvent;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.OutboxRepository;
import com.example.orderservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Service
public class EventListner {
    private KafkaConfigLoader configLoader;

    private OrderRepository orderRepository;

    private OutboxRepository outboxRepository;

    private ProcessedEventRepository processedEventRepository;

    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> consumer;

    @Autowired
    public EventListner(ProcessedEventRepository processedEventRepository, OutboxRepository outboxRepository, OrderRepository orderRepository, KafkaConfigLoader configLoader){
        this.processedEventRepository=processedEventRepository;
        this.outboxRepository=outboxRepository;
        this.orderRepository=orderRepository;
        this.configLoader = configLoader;
        this.objectMapper=new ObjectMapper();
        Properties props=configLoader.getConsumerProperties();
        this.consumer=new KafkaConsumer<>(props);
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void eventListenerThread() {
        Thread thread = new Thread(() -> {
            try {
                handle();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(false);
        thread.start();
    }

    public void handle() throws JsonProcessingException {
        consumer.subscribe(List.of("InventoryRejected","PaymentAuthorized","PaymentRejected"));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    handleEvent(record);
                    consumer.commitAsync();
                } catch (JsonProcessingException e) {
                    sendToDLQ(record, e);
                }
            }
        }
    }

    @Transactional
    private void handleEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String payload=record.value();
        String topic=record.topic();

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
           System.out.println("order canceled wit id:"+ orderId);
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
           System.out.println("order complted wit id:"+ orderId);

           outboxRepository.save(outbox);
        }

       processedEventRepository.save(new ProcessedEvent(orderId));
       orderRepository.save(order);
    }

    private void sendToDLQ(ConsumerRecord<String, String> record, Exception e){
        KafkaProducer<String, String> dlqProducer = new KafkaProducer<>(configLoader.getProducerProperties());
        ProducerRecord<String, String> rec = new ProducerRecord<>("InventoryDLQ", record.value());
        rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
        dlqProducer.send(rec);
    }
}
