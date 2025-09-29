package com.example.inventoryservice.service;

import com.example.inventoryservice.config.KafkaConfigLoader;
import com.example.inventoryservice.dto.InventoryOrderItem;
import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.OutboxRepository;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {
    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private KafkaConfigLoader configLoader;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private KafkaConsumer<String, String> kafkaConsumer=new KafkaConsumer<>(configLoader.getConsumerProperties());

    ObjectMapper objectMapper = new ObjectMapper();



    /**
     * Processes orders from the "OrderCreated" topic. This method subscribes to the topic,
     * polls for new records every 100 milliseconds, and processes each record by
     * calling {@link #handleOrder(ConsumerRecord)}. If {@link #handleOrder(ConsumerRecord)}
     * throws a {@link JsonProcessingException}, the method sends the record to the dead letter queue
     * by calling {@link #sendToDLQ(ConsumerRecord, Exception)}.
     *
     * @throws JsonProcessingException if a record cannot be parsed into an {@link OrderCreated} object
     */
    public void processOrder() throws JsonProcessingException {
        kafkaConsumer.subscribe(List.of("OrderCreated"));
        while (true){
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records){
                try{
                    handleOrder(record);
                } catch (JsonProcessingException e) {
                    sendToDLQ(record, e);
                }
            }
        }
    }

    private void handleOrder(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String jsonPayload = record.value();
        Headers headers = record.headers();

        OrderCreated order = objectMapper.readValue(jsonPayload, OrderCreated.class);
        UUID orderId = order.getId();

        if(processedEventRepository.findByEventId(orderId)){
            return;
        }

        //check if stock is available and reserve stock
        boolean reserved=reserveStock(order);
        String status=reserved==true?"InventoryReserved":"InventoryFailed";

        //create Outbox Event
        Outbox outbox=new Outbox();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateId(orderId);
        outbox.setType(status);
        outbox.setStatus("PENDING");
        outbox.setPayload(objectMapper.writeValueAsString(order));
        outbox.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outbox);

        //save  proccessed event
        ProcessedEvent processedEvent=new ProcessedEvent(orderId);
        processedEventRepository.save(processedEvent);

        kafkaConsumer.commitAsync();
    }

    /**
     * Reserves stock for given order.
     * Iterates over each order item and checks if the available quantity is enough to fulfill the order.
     * If the available quantity is not enough, returns false.
     * If the available quantity is enough, creates a reservation for the order item and updates the available quantity.
     * @param order the order to reserve stock for
     * @return true if the stock was reserved successfully, false otherwise
     */
    private boolean reserveStock(OrderCreated order){
        for(InventoryOrderItem item: order.getOrderItems()){
            Stock stock = stockRepository.findBySku(item.getSku());
            Long availableQuantity = stock.getAvailable();
            String result;
            if(item.getQuantity()>=availableQuantity){
               return false;
            }else{
                Reservation reservation = new Reservation(order.getId(),item.getSku(), item.getQuantity());
                stock.setAvailable(availableQuantity-item.getQuantity());
                stockRepository.save(stock);
                reservationRepository.save(reservation);
            }
        }
        return true;
    }

    /**
     * Sends a record to the dead letter queue with an error header set to the message of the given exception.
     * @param record the record to send to the dead letter queue
     * @param e the exception to set as the error header
     */
    private void sendToDLQ(ConsumerRecord<String, String> record, Exception e){
        KafkaProducer<String, String> dlqProducer = new KafkaProducer<>(configLoader.getProducerProperties());
        ProducerRecord<String, String> rec = new ProducerRecord<>("InventoryDLQ", record.value());
        rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
        dlqProducer.send(rec);
    }

}
