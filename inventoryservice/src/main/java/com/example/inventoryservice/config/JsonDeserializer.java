package com.example.inventoryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;
import java.util.HashMap;

public class JsonDeserializer<T> implements Deserializer<T> {

    private final ObjectMapper objectMapper;
    private Map<String, Class<?>> topicClassMap = new HashMap<>();

    public JsonDeserializer() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String mappingStr = (String) configs.get("consumer.json.value.type.map");
        if (mappingStr != null) {
            String[] mappings = mappingStr.split(",");
            for (String mapping : mappings) {
                String[] parts = mapping.split("=");
                if (parts.length == 2) {
                    String topic = parts[0].trim();
                    String className = parts[1].trim();
                    try {
                        Class<?> clazz = Class.forName(className);
                        System.out.println("----------");
                        System.out.println("Topic: " + topic + ", Target class: " + clazz);
                        topicClassMap.put(topic, clazz);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Failed to load class for topic deserialization: " + className, e);
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(String topic, byte[] data) {
        try {
            if (data == null || data.length == 0) return null;
            Class<?> targetClass = topicClassMap.get(topic);
            if (targetClass == null) {
                throw new RuntimeException("No target class configured for topic: " + topic);
            }
            return (T) objectMapper.readValue(data, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON message from topic: " + topic, e);
        }
    }

    @Override
    public void close() {
        // nothing to close
    }
}
