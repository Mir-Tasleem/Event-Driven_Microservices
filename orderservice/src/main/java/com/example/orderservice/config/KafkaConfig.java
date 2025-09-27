package com.example.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic ordersCreated(){
        return new NewTopic("orders.created",3, (short) 1);
    }

    @Bean
    public NewTopic ordersCompleted(){
        return new NewTopic("orders.completed",3, (short) 1);
    }

    @Bean
    public NewTopic ordersCancelled(){
        return new NewTopic("orders.cancelled",3, (short) 1);
    }

    @Bean
    public NewTopic inventoyReserved(){
        return new NewTopic("inventory.reserved",3, (short) 1);
    }

    @Bean
    public NewTopic inventoyRejected(){
        return new NewTopic("inventory.rejected",3, (short) 1);
    }

    @Bean
    public NewTopic paymentAuthorized(){
        return new NewTopic("payment.authorized",3, (short) 1);
    }

    @Bean
    public NewTopic notificationSent(){
        return new NewTopic("notification.sent",3, (short) 1);
    }
}
