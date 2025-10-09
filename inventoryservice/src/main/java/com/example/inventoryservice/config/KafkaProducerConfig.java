package com.example.inventoryservice.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KafkaProducerConfig {
    @Bean
    @Qualifier("paymentDLQProducer")
    public KafkaProducer<String, String> paymentDLQProducer(KafkaConfigLoader loader) {
        Properties props = loader.getProducerProperties();
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "payment-dlq-tx");
        return new KafkaProducer<>(props);
    }

    @Bean
    @Qualifier("inventoryDLQProducer")
    public KafkaProducer<String, String> inventoryDLQProducer(KafkaConfigLoader loader) {
        Properties props = loader.getProducerProperties();
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "inventory-dlq-tx");
        return new KafkaProducer<>(props);
    }
}


