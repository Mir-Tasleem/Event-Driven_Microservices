package com.example.inventoryservice.config;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
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
    @Qualifier("orderConsumer")
    public KafkaConsumer<String, String> OrderConsumer() {
        Properties props = configLoader.getConsumerProperties();
        return new KafkaConsumer<>(props);
    }

    @Bean
    @Qualifier("paymentConsumer")
    public KafkaConsumer<String, String> paymentFailedConsumer() {
        Properties props = configLoader.getConsumerProperties();
        // Optionally, set group.id or other props differently for topic2
        return new KafkaConsumer<>(props);
    }
}
