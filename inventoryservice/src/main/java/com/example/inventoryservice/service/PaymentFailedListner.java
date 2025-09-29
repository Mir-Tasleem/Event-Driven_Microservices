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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

@Service
public class PaymentFailedListner {
    @Service
    public class PaymentFailureHandler {

        @Autowired
        private ReservationRepository reservationRepository;
        @Autowired
        private StockRepository stockRepository;
        @Autowired
        private ProcessedEventRepository processedEventRepository;
        @Autowired
        private KafkaConfigLoader configLoader;
        private final KafkaConsumer<String, String> consumer;

        private final ObjectMapper mapper = new ObjectMapper();

        public PaymentFailureHandler(KafkaConfigLoader loader) {
            this.consumer = new KafkaConsumer<>(loader.getConsumerProperties());
        }

        @PostConstruct
        public void start() {
            consumer.subscribe(List.of("PaymentFailed"));

            Executors.newSingleThreadExecutor().submit(() -> {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                    for (ConsumerRecord<String, String> rec : records) {
                        try {
                            handlePaymentFailed(rec.value());
                            consumer.commitAsync();
                        } catch (Exception ex) {
                            // optional: send to DLQ
                        }
                    }
                }
            });
        }

        private void handlePaymentFailed(String json) throws JsonProcessingException {
            PaymentFailedEvent event = mapper.readValue(json, PaymentFailedEvent.class);

            // idempotency check
            if (processedEventRepository.findByEventId(event.getId())) return;

            // 1️⃣ find reservations for this order
            List<Reservation> reservations = reservationRepository.findByOrderId(event.getOrderId());
            for (Reservation r : reservations) {
                Stock stock = stockRepository.findBySku(r.getSku());
                stock.setAvailable(stock.getAvailable() + r.getQuantity());
                stockRepository.save(stock);
                reservationRepository.delete(r);
            }

            processedEventRepository.save(new ProcessedEvent(event.getOrderId()));
        }
    }

}
