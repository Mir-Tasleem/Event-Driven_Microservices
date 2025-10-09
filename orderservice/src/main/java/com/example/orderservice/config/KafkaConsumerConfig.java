package com.example.orderservice.config;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KafkaConsumerConfig {
    private final KafkaConfigLoader configLoader;

    public KafkaConsumerConfig(KafkaConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Bean
    public KafkaConsumer<String, String> kafkaConsumer() {
        Properties props = configLoader.getConsumerProperties();
        return new KafkaConsumer<>(props);
    }
}
