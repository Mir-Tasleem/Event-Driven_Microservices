package com.example.paymentservice.service;

import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.repository.OutboxRepository;
import com.example.paymentservice.config.KafkaConfigLoader;
import com.example.paymentservice.dto.OrderRecieved;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.processedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    @Autowired
    private processedEventRepository processedEventRepository;

    @Autowired
    private KafkaConfigLoader configLoader;

    @Autowired
    private OutboxRepository outboxRepository;


    private KafkaConsumer<String, String> kafkaConsumer=new KafkaConsumer<>(configLoader.getConsumerProperties());

    private ObjectMapper objectMapper=new ObjectMapper();
    public void processPayment() throws JsonProcessingException {
        kafkaConsumer.subscribe(List.of("InventoryReserved"));

        while (true){
            ConsumerRecords<String, String> recs=kafkaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> rec:recs){
                String jsonPayload=rec.value();
                Headers headers=rec.headers();

                OrderRecieved order=objectMapper.readValue(jsonPayload, OrderRecieved.class);
                UUID orderId=order.getId();

                if(processedEventRepository.findByEventId(orderId)){
                    return;
                }

                boolean paid=doPayment(order);
                String status=paid==true?"PaymentSuccess":"PaymentFailed";

                //create payment
                Payment payment=new Payment();
                payment.setId(UUID.randomUUID());
                payment.setOrderId(orderId);
                payment.setAmount(order.getTotalAmount());
                payment.setStatus(status);
                payment.setProviderRef("ProviderRef");

                //create outbox event
                Outbox outbox=new Outbox();
                outbox.setId(UUID.randomUUID());
                outbox.setAggregateId(orderId);
                outbox.setType(status);
                outbox.setStatus("PENDING");
                outbox.setPayload(objectMapper.writeValueAsString(order));
                outbox.setCreatedAt(LocalDateTime.now());
                outboxRepository.save(outbox);

                //save processed event
                ProcessedEvent processedEvent=new ProcessedEvent(orderId);
                processedEventRepository.save(processedEvent);
            }
        }

    }

    private boolean doPayment(OrderRecieved order){
        //payment business logic
        if(order.getTotalAmount()%2==0){
            return true;
        }
        return false;
    }
}
