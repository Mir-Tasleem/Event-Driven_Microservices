package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.InventoryOrderItem;
import com.example.inventoryservice.dto.OrderCreated;
import com.example.inventoryservice.model.Reservation;
import com.example.inventoryservice.model.Stock;
import com.example.inventoryservice.repository.ReservationRepository;
import com.example.inventoryservice.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
    private StockRepository stockRepository;
    private ReservationRepository reservationRepository;

    StockService(StockRepository stockRepository, ReservationRepository reservationRepository){
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserveStock(OrderCreated order){
        for(InventoryOrderItem item: order.getOrderItems()){
            Stock stock = stockRepository.findBySku(item.getSku());
            if( stock==null || item.getQuantity()>stock.getAvailable()){
                return false;
            }else{
                Reservation reservation = new Reservation(order.getId(),item.getSku(), item.getQuantity());
                stock.setAvailable(stock.getAvailable()-item.getQuantity());
                stock.setReserved(stock.getReserved()+item.getQuantity());
                stockRepository.save(stock);
                reservationRepository.save(reservation);
            }
        }
        return true;
    }
}
