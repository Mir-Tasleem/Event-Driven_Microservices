package com.example.inventoryservice.util;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Simple helper to create ConsumerRecord instances for unit testing.
 */
public class TestUtils {

    public static ConsumerRecord<String, String> createConsumerRecord(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "test-key", value);
    }
}
