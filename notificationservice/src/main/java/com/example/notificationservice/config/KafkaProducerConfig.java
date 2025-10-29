package com.example.notificationservice.config;

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
    public KafkaProducer<String, String> dlqProducer(KafkaConfigLoader loader) {
        Properties props = loader.getProducerProperties();
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "notification-dlq-tx");
        return new KafkaProducer<>(props);
    }
}


