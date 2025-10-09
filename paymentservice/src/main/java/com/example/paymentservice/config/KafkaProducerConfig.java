package com.example.paymentservice.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KafkaProducerConfig {
    private final KafkaConfigLoader configLoader;

    public KafkaProducerConfig(KafkaConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Bean
    public KafkaProducer<String, String> kafkaProducer() {
        Properties props = configLoader.getProducerProperties();
        return new KafkaProducer<>(props);
    }
}
