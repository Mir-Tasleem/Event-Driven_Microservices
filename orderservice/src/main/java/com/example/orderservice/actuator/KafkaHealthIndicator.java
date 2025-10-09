package com.example.orderservice.actuator;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Component
public class KafkaHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Properties props = new Properties();
        props.put("bootstrap.servers", 29092);

        try (AdminClient client = AdminClient.create(props)) {
            DescribeClusterResult result = client.describeCluster();
            result.nodes().get(); // check connection
            return Health.up().withDetail("Kafka", "Available").build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).withDetail("Kafka", "Interrupted").build();
        } catch (ExecutionException e) {
            return Health.down(e.getCause()).withDetail("Kafka", "Not available").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("Kafka", "Unknown error").build();
        }
    }
}
