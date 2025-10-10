package com.example.paymentservice.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(exception= JsonProcessingException.class)
    public ResponseEntity<String> handleException(JsonProcessingException jsonProcessingException){
        return new ResponseEntity<>(jsonProcessingException.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}