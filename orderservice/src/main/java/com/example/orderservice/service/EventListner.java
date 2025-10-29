package com.example.orderservice.service;

import com.example.orderservice.config.KafkaConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class EventListner {
    private KafkaConfigLoader configLoader;
    private EventHandlerService eventHandlerService;
    private ObjectMapper objectMapper;
    private KafkaConsumer<String, String> consumer;

    @Autowired
    public EventListner(KafkaConsumer<String, String> kafkaConsumer,
                        EventHandlerService eventHandlerService,
                          KafkaConfigLoader configLoader){
        this.eventHandlerService=eventHandlerService;
        this.configLoader = configLoader;
        this.objectMapper=new ObjectMapper();
        this.consumer=kafkaConsumer;
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void eventListenerThread() {
        try {
            handle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handle(){
        consumer.subscribe(List.of("InventoryRejected","PaymentAuthorized","PaymentRejected"));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> rec : records) {
                handleWithRetry(rec);
            }
        }
    }

    private void handleWithRetry(ConsumerRecord<String, String> rec){
        int maxRetries=3;
        int attempt=0;
        long backoffMillis = 2000;
        boolean success=false;
        while(attempt<maxRetries && !success){
            try{
                eventHandlerService.handleEvent(rec);
                success=true;
            }catch (Exception e){
                attempt++;
                if(attempt<maxRetries){
                    sleep(backoffMillis);
                }else{
                    sendToDLQ(rec, e);
                }
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


     void sendToDLQ(ConsumerRecord<String, String> consumerRecord, Exception e){
        KafkaProducer<String, String> dlqProducer = new KafkaProducer<>(configLoader.getProducerProperties());
        ProducerRecord<String, String> rec = new ProducerRecord<>("InventoryDLQ", consumerRecord.value());
        rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
        dlqProducer.send(rec);
        dlqProducer.close();
    }
}
