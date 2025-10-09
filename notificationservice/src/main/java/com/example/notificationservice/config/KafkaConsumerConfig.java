package com.example.notificationservice.config;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConsumerConfig {
    private final KafkaConfigLoader kafkaConfigLoader;

    public KafkaConsumerConfig(KafkaConfigLoader kafkaConfigLoader) {
        this.kafkaConfigLoader = kafkaConfigLoader;
    }

    @Bean
    public KafkaConsumer<String, String> kafkaConsumer() {
        return new KafkaConsumer<>(kafkaConfigLoader.getConsumerProperties());
    }
}
