package com.example.orderservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class JsonDeserializer<T> implements Deserializer<T> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Class<T> targetClass;

    // Default constructor required by Kafka
    public JsonDeserializer() {
    }

    public JsonDeserializer(Class<T> targetClass) {
        this.targetClass = targetClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Get the target class from config if not set through constructor
        if (targetClass == null) {
            String targetClassConfig = isKey ? 
                (String) configs.get("key.deserializer.class") : 
                (String) configs.get("value.deserializer.class");
            try {
                if (targetClassConfig != null) {
                    targetClass = (Class<T>) Class.forName(targetClassConfig);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to initialize JsonDeserializer: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        try {
            if (data == null || data.length == 0) return null;
            return objectMapper.readValue(data, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON message", e);
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
