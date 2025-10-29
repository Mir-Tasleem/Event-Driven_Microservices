package com.example.paymentservice.exception;

public class ProducerNotInitialisedException extends RuntimeException{
    public ProducerNotInitialisedException(String message, Exception e) {
        super(message,e);
    }
}
