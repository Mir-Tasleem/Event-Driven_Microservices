package com.example.inventoryservice.service;

import com.example.inventoryservice.config.KafkaConfigLoader;
import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.dto.PaymentFailedEvent;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;


@Service
public class PaymentFailedListner {
    private boolean initialized = false;
    private final Object initLock = new Object();
    private ReservationRepository reservationRepository;
    private StockRepository stockRepository;
    private ProcessedEventRepository processedEventRepository;
    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> dlqProducer;
    private  KafkaConfigLoader configLoader;

    private final ObjectMapper mapper = new ObjectMapper();

    public PaymentFailedListner(KafkaConfigLoader configLoader,ReservationRepository reservationRepository,
                                StockRepository stockRepository, ProcessedEventRepository processedEventRepository,
                                @Qualifier("paymentConsumer") KafkaConsumer<String, String> consumer, @Qualifier("paymentDLQProducer") KafkaProducer<String, String> dlqProducer) {
        this.consumer = consumer;
        this.dlqProducer=dlqProducer;
        this.configLoader=configLoader;
        this.reservationRepository=reservationRepository;
        this.stockRepository=stockRepository;
        this.processedEventRepository=processedEventRepository;
    }

    @PostConstruct
    public void start() {
        consumer.subscribe(List.of("PaymentFailed"));

        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<String, String> rec : records) {
                    int maxRetries=3;
                    int attempt=0;
                    long backoffMillis = 2000;
                    boolean success=false;
                    while(attempt<maxRetries && !success){
                        try{
                            handlePaymentFailed(rec.value());
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
        });
    }

    @Transactional(rollbackFor = Exception.class)
    private void handlePaymentFailed(String json) throws JsonProcessingException {
        PaymentFailedEvent event = mapper.readValue(json, PaymentFailedEvent.class);

        // idempotency check
        if (processedEventRepository.existsByEventId(event.getId())) return;

        List<Reservation> reservations = reservationRepository.findByOrderId(event.getOrderId());
        for (Reservation r : reservations) {
            Stock stock = stockRepository.findBySku(r.getSku());
            stock.setAvailable(stock.getAvailable() + r.getQuantity());
            stockRepository.save(stock);
            reservationRepository.delete(r);
        }

        processedEventRepository.save(new ProcessedEvent(event.getOrderId()));
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

