package com.example.inventoryservice.config;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestKafkaConfig {

    @Bean
    @Primary
    public KafkaConsumer<String, String> testKafkaConsumer() {
        // Mocked consumer — won't connect to any broker
        return mock(KafkaConsumer.class);
    }

    @Bean
    @Primary
    public KafkaProducer<String, String> testKafkaProducer() {
        // Mocked producer — no broker needed
        return mock(KafkaProducer.class);
    }
}
