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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
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

    public PaymentService( KafkaConsumer<String, String> kafkaConsumer,ProcessedEventRepository processedEventRepository, KafkaConfigLoader configLoader, OutboxRepository outboxRepository, PaymentRepository paymentRepository){
        this.processedEventRepository=processedEventRepository;
        this.outboxRepository=outboxRepository;
        this.paymentRepository=paymentRepository;
        this.kafkaConsumer=kafkaConsumer;
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void startConsumerThread() {
        try {
            processPayment();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processPayment(){
        kafkaConsumer.subscribe(List.of("InventoryReserved"));
        while (true){
            ConsumerRecords<String, String> recs=kafkaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> rec:recs){
                int maxRetries=3;
                int attempt=0;
                long backoffMillis = 2000;
                boolean success=false;
                while(attempt<maxRetries && !success){
                    try{
                        handlePayment(rec);
                        success=true;
                    }catch (Exception e){
                        attempt++;
                        if(attempt<maxRetries){
                            try {
                                Thread.sleep(backoffMillis);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }else{
                            sendToDLQ(rec, e);
                        }
                    }
                }
            }
        }

    }

    @Transactional(rollbackOn = Exception.class)
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
        System.out.println("status length: "+outbox.getStatus().length());
        System.out.println("payload length: "+outbox.getPayload().length());
        outboxRepository.save(outbox);

        kafkaConsumer.commitAsync();
    }

    private boolean doPayment(OrderRecieved order){
        if(order.getTotalAmount()%2==0){
            return true;
        }else {
            return false;
        }
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
