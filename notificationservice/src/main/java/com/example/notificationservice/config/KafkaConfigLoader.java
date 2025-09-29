package com.example.notificationservice.config;

import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Configuration
public class KafkaConfigLoader {

    private static Properties load(String filename) {
        Properties props = new Properties();
        try (InputStream input = KafkaConfigLoader.class.getClassLoader()
                .getResourceAsStream(filename)) {
            if (input == null) {
                throw new FileNotFoundException("Kafka config file not found: " + filename);
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Kafka properties from " + filename, e);
        }
        return props;
    }

    private static Properties extractProperties(Properties allProps, String prefix) {
        Properties filtered = new Properties();
        for (String name : allProps.stringPropertyNames()) {
            if (name.startsWith(prefix + ".")) {
                filtered.put(name.substring(prefix.length() + 1), allProps.getProperty(name));
            }
        }
        return filtered;
    }

    public static Properties getConsumerProperties() {
        Properties allProps = load("kafka-config.properties");
        Properties consumerProps = extractProperties(allProps, "consumer");
        // Add common properties
        String bootstrapServers = allProps.getProperty("bootstrap.servers");
        if (bootstrapServers != null) {
            consumerProps.put("bootstrap.servers", bootstrapServers);
        }
        return consumerProps;
    }

    public static Properties getProducerProperties() {
        Properties allProps = load("kafka-config.properties");
        Properties producerProps = extractProperties(allProps, "producer");
        // Add common properties
        String bootstrapServers = allProps.getProperty("bootstrap.servers");
        if (bootstrapServers != null) {
            producerProps.put("bootstrap.servers", bootstrapServers);
        }
        return producerProps;
    }
}
