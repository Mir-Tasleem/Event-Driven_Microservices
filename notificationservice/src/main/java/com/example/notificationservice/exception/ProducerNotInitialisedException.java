package com.example.notificationservice.exception;

public class ProducerNotInitialisedException extends RuntimeException{
    public ProducerNotInitialisedException(String message, Exception e) {
        super(message,e);
    }
}
