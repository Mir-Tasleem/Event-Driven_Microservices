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
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
public class InventoryService {
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    private StockRepository stockRepository;
    private ReservationRepository reservationRepository;
    private OutboxRepository outboxRepository;
    private ProcessedEventRepository processedEventRepository;
    private final KafkaConsumer<String, String> kafkaConsumer;
    private final KafkaProducer<String, String> dlqProducer;

    private ObjectMapper objectMapper = new ObjectMapper();

    public InventoryService(StockRepository stockRepository,@Qualifier("orderConsumer") KafkaConsumer<String, String> kafkaConsumer,
                            ReservationRepository reservationRepository, OutboxRepository outboxRepository,
                            ProcessedEventRepository processedEventRepository,@Qualifier("inventoryDLQProducer") KafkaProducer<String, String> dlqProducer){
        this.stockRepository=stockRepository;
        this.reservationRepository=reservationRepository;
        this.outboxRepository=outboxRepository;
        this.processedEventRepository=processedEventRepository;
        this.kafkaConsumer=kafkaConsumer;
        this.dlqProducer=dlqProducer;
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }


    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void startConsumerThread() {
        try {
            processOrder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void processOrder() {
        kafkaConsumer.subscribe(List.of("OrderCreated"));
        while (true){
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records){
               int maxRetries=3;
               int attempt=0;
               long backoffMillis = 2000;
               boolean success=false;
               while(attempt<maxRetries && !success){
                   try{
                       handleOrder(record);
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
                          sendToDLQ(record, e);
                      }
                   }
               }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleOrder(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String jsonPayload=record.value();
        Headers headers = record.headers();
        OrderCreated order = objectMapper.readValue(jsonPayload, OrderCreated.class);
        UUID orderId = order.getId();

        if(processedEventRepository.existsByEventId(orderId)){
            return;
        }

        //check if stock is available and reserve stock
        boolean reserved=reserveStock(order);
        String status=reserved==true?"InventoryReserved":"InventoryRejected";

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
    @Transactional(propagation = Propagation.MANDATORY)
    private boolean reserveStock(OrderCreated order){
        for(InventoryOrderItem item: order.getOrderItems()){
            Stock stock = stockRepository.findBySku(item.getSku());
            if( stock==null || item.getQuantity()>stock.getAvailable()){
                return false;
            }else{
                Reservation reservation = new Reservation(order.getId(),item.getSku(), item.getQuantity());
                stock.setAvailable(stock.getAvailable()-item.getQuantity());
                stock.setReserved(stock.getReserved()+item.getQuantity());
                stockRepository.save(stock);
                reservationRepository.save(reservation);
            }
        }
        return true;
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
    /**
     * Sends a record to the dead letter queue with an error header set to the message of the given exception.
     * @param record the record to send to the dead letter queue
     * @param e the exception to set as the error header
     */
    private void sendToDLQ(ConsumerRecord<String, String> record, Exception e){
        initializeTransactions(dlqProducer);
        try {
            dlqProducer.beginTransaction();
            ProducerRecord<String, String> rec = new ProducerRecord<>("InventoryDLQ", record.value());
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