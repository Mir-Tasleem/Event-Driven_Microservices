package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.model.Outbox;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.repository.OutboxRepository;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderProcessorService {
    private ObjectMapper objectMapper=new ObjectMapper();
    private final StockService stockService;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventRepository processedEventRepository;

    public OrderProcessorService(StockService stockService, OutboxRepository outboxRepository, ProcessedEventRepository processedEventRepository) {
        this.stockService = stockService;
        this.outboxRepository = outboxRepository;
        this.processedEventRepository = processedEventRepository;
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleOrder(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        String jsonPayload=rec.value();
        OrderCreated order = objectMapper.readValue(jsonPayload, OrderCreated.class);
        UUID orderId = order.getId();

        if(processedEventRepository.existsByEventId(orderId)){
            return;
        }

        //check if stock is available and reserve stock
        boolean reserved=stockService.reserveStock(order);
        String status=reserved ? "InventoryReserved":"InventoryRejected";

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
    }
}
