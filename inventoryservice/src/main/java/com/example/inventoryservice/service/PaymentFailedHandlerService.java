package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.PaymentFailedEvent;
import com.example.inventoryservice.model.ProcessedEvent;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.ProcessedEventRepository;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentFailedHandlerService {

    private final ObjectMapper mapper;
    private final ReservationRepository reservationRepository;
    private final StockRepository stockRepository;
    private final ProcessedEventRepository processedEventRepository;

    PaymentFailedHandlerService(ReservationRepository reservationRepository, StockRepository stockRepository, ProcessedEventRepository processedEventRepository) {
        this.reservationRepository = reservationRepository;
        this.stockRepository = stockRepository;
        this.processedEventRepository = processedEventRepository;
        mapper=new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentFailed(String json) throws JsonProcessingException {
        PaymentFailedEvent event = mapper.readValue(json, PaymentFailedEvent.class);

        // idempotency check
        if (processedEventRepository.existsByEventId(event.getId())) return;

        List<Reservation> reservations = reservationRepository.findByOrderId(event.getOrderId());
        for (Reservation r : reservations) {
            Stock stock = stockRepository.findBySku(r.getSku());
            stock.setAvailable(stock.getAvailable() + r.getQuantity());
            stockRepository.save(stock);
            reservationRepository.delete(r);
        }

        processedEventRepository.save(new ProcessedEvent(event.getOrderId()));
    }


}
