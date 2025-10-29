package com.example.inventoryservice.exception;

public class ProducerNotInitialisedException extends RuntimeException{
    public ProducerNotInitialisedException(String message,Exception e) {
        super(message,e);
    }
}
