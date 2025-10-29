package com.example.inventoryservice.service;

import com.example.inventoryservice.exception.ProducerNotInitialisedException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class PaymentFailedListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentFailedListener.class);
    private boolean initialized = false;
    private final Object initLock = new Object();
    private final PaymentFailedHandlerService paymentFailedHandlerService;
    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> dlqProducer;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();


    public PaymentFailedListener(PaymentFailedHandlerService paymentFailedHandlerService,
                                 @Qualifier("paymentConsumer") KafkaConsumer<String, String> consumer, @Qualifier("paymentDLQProducer") KafkaProducer<String, String> dlqProducer) {
        this.consumer = consumer;
        this.dlqProducer=dlqProducer;
        this.paymentFailedHandlerService=paymentFailedHandlerService;
    }

    @PostConstruct
    public void start() {
        consumer.subscribe(List.of("PaymentFailed"));
        executor.submit(this::consumerecords);
    }

    private void consumerecords() {
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> rec : records) {
                processWithRetry(rec);
            }
        }
    }

    private void processWithRetry(ConsumerRecord<String, String> rec) {
        int maxRetries = 3;
        long backoffMillis = 2000;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try {
                paymentFailedHandlerService.handlePaymentFailed(rec.value());
                success = true;
            } catch (Exception e) {
                attempt++;
                if (attempt < maxRetries) {
                    sleep(backoffMillis);
                } else {
                    sendToDLQ(rec, e);
                }
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    private void initializeTransactions(KafkaProducer<String, String> producer) {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        producer.initTransactions();
                        initialized = true;
                    } catch (Exception e) {
                        throw new ProducerNotInitialisedException("Failed to initialize transactions", e);
                    }
                }
            }
        }
    }

    private void sendToDLQ(ConsumerRecord<String, String> conRec, Exception e){
        initializeTransactions(dlqProducer);
        try {
            dlqProducer.beginTransaction();
            ProducerRecord<String, String> rec = new ProducerRecord<>("PaymentDLQ", conRec.value());
            rec.headers().add(new RecordHeader("error",e.getMessage().getBytes(StandardCharsets.UTF_8)));
            dlqProducer.send(rec).get();
            dlqProducer.commitTransaction();
        }catch (InterruptedException ie){
            Thread.currentThread().interrupt();
            log.error("Thread was interrupted while sending to DLQ", ie);
            safelyAbortTransaction();
        }catch (Exception ex){
            safelyAbortTransaction();
            log.error("Failed to send to DLQ: {}" , ex.getMessage());
        }
        dlqProducer.close();
    }

    private void safelyAbortTransaction() {
        try {
            dlqProducer.abortTransaction();
        } catch (Exception abortEx) {
            log.error("Failed to abort DLQ transaction: {}", abortEx.getMessage(), abortEx);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (consumer != null) {
                consumer.wakeup();
                consumer.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

}

