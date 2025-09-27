package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/")
public class OrderController {
    @Autowired
    private OrderService orderService;
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest orderRequest,@RequestHeader("Idempotency-Key") String idempotencyKey) {
        OrderResponse orderResponse=new OrderResponse();
        try{
            orderService.createOrder(orderRequest,idempotencyKey);
        } catch (JsonProcessingException e) {
            return new ResponseEntity<>(orderResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok(orderResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity.ok(orderResponse);
    }
}
