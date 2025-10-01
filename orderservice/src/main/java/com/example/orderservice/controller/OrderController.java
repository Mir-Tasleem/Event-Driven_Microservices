package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/")
public class OrderController {
    @Autowired
    private OrderService orderService;
    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody OrderRequest orderRequest) {
        UUID orderId;
        OrderResponse orderResponse=new OrderResponse();
        try{
            orderId=orderService.createOrder(orderRequest);
        } catch (JsonProcessingException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Map<String, String> response=new HashMap<>();
        response.put("orderId", orderId.toString());
        response.put("status", "Pending");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity.ok(orderResponse);
    }
}
