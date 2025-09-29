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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventListner {
    @Autowired
    private KafkaConfigLoader configLoader;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;
    private ObjectMapper objectMapper=new ObjectMapper();

    KafkaConsumer<String, String> consumer=new KafkaConsumer<>(configLoader.getConsumerProperties());

    public void handle() throws JsonProcessingException {
        consumer.subscribe(List.of("inventory.reserved","inventory.rejected","payment.authroized","payment.rejected"));

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

    private void handleEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String payload=record.value();
        String topic=record.topic();

        Order order=objectMapper.readValue(payload, Order.class);
        UUID orderId=order.getId();


        //idempotency check
        if(processedEventRepository.existsById(orderId)){
            return;
        }

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
