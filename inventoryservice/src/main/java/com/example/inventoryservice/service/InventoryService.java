package com.example.inventoryservice.service;

import com.example.inventoryservice.config.KafkaConfigLoader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

@Service
public class InventoryService {
    @Autowired
    private KafkaConfigLoader configLoader;

    public void processEvent(String event) {
        Properties consumerProps = configLoader.getConsumerProperties();

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(List.of("OrderCreated"));
        while (true){
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records){
                String JsonPayload = record.value();
                Headers headers = record.headers();


            }
        }

    }

}
