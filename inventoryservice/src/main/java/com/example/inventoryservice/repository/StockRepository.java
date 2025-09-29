package com.example.inventoryservice.repository;

import com.example.inventoryservice.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {
    Stock findBySku(String sku);

    Optional<Long> findQuantityBySku(String sku);
}
