package com.example.paymentservice.service;

import com.example.paymentservice.model.Outbox;
import com.example.paymentservice.model.ProcessedEvent;
import com.example.paymentservice.repository.OutboxRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedEventRepository;
import com.example.paymentservice.config.KafkaConfigLoader;
import com.example.paymentservice.dto.OrderRecieved;
import com.example.paymentservice.model.Payment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
public class PaymentService {
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    private ProcessedEventRepository processedEventRepository;
    private KafkaConfigLoader configLoader;
    private OutboxRepository outboxRepository;
    private KafkaConsumer<String, String> kafkaConsumer;
    private PaymentRepository paymentRepository;

    private ObjectMapper objectMapper=new ObjectMapper();

    public PaymentService(ProcessedEventRepository processedEventRepository, KafkaConfigLoader configLoader, OutboxRepository outboxRepository, PaymentRepository paymentRepository){
        this.processedEventRepository=processedEventRepository;
        this.outboxRepository=outboxRepository;
        this.paymentRepository=paymentRepository;
        Properties props=configLoader.getConsumerProperties();
        props.put("consumer.json.value.type.map", "InventoryReserved=com.example.paymentservice.dto.OrderRecieved");
        kafkaConsumer=new KafkaConsumer<>(props);
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumerThread() {
        Thread thread = new Thread(() -> {
            try {
                processPayment();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(false);
        thread.start();
    }

    public void processPayment(){
        kafkaConsumer.subscribe(List.of("InventoryReserved"));
        while (true){
            ConsumerRecords<String, String> recs=kafkaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> rec:recs){
                System.out.println("-------");
                System.out.println(rec.toString());
                try{
                    handlePayment(rec);
                } catch (JsonProcessingException e) {
                    sendToDLQ(rec, e);
                }
            }
        }

    }

    @Transactional
    private void handlePayment(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        String jsonPayload=rec.value();
        Headers headers=rec.headers();

        OrderRecieved order=objectMapper.readValue(jsonPayload, OrderRecieved.class);
        UUID orderId=order.getId();

        if(processedEventRepository.existsByEventId(orderId)){
            return;
        }

        boolean paid=doPayment(order);
        String status=paid==true?"PaymentAuthorized":"PaymentRejected";

        //create payment
        Payment payment=new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(orderId);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(status);
        payment.setProviderRef("ProviderRef");
        paymentRepository.save(payment);

        //save processed event
        ProcessedEvent processedEvent=new ProcessedEvent(orderId);
        processedEventRepository.save(processedEvent);

        //create outbox event
        Outbox outbox=new Outbox();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateId(orderId);
        outbox.setType(status);
        outbox.setStatus("PENDING");
        outbox.setPayload(objectMapper.writeValueAsString(order));
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);

        kafkaConsumer.commitAsync();
    }

    private boolean doPayment(OrderRecieved order){
        //payment business logic
        if(order.getTotalAmount()%2==0){
            return true;
        }
        return false;
    }

    private void initializeTransactions(KafkaProducer<String, String> producer) {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        producer.initTransactions();
                        initialized = true;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize transactions", e);
                    }
                }
            }
        }
    }
    private void sendToDLQ(ConsumerRecord<String, String> record, Exception e){
        Properties dlqprops=configLoader.getProducerProperties();
        dlqprops.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,"payment-dlq-tx");
        KafkaProducer<String, String> dlqProducer = new KafkaProducer<>(dlqprops);
        initializeTransactions(dlqProducer);
        try {
            dlqProducer.beginTransaction();
            ProducerRecord<String, String> rec = new ProducerRecord<>("PaymentDLQ", record.value());
            rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
            dlqProducer.send(rec).get();
            dlqProducer.commitTransaction();
        }catch (Exception ex){
            try {
                dlqProducer.abortTransaction();
            } catch (Exception abortEx) {
                System.err.println("Failed to abort transaction: " + abortEx.getMessage());
            }
            System.err.println("Failed to send to DLQ: " + ex.getMessage());
        }
        dlqProducer.close();
    }
}
