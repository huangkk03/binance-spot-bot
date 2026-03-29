package com.binance.compound.repository;

import com.binance.compound.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    
    List<PriceAlert> findByTriggered(Boolean triggered);
    
    List<PriceAlert> findBySymbolAndInterval(String symbol, String interval);
    
    Optional<PriceAlert> findBySymbolAndIntervalAndAlertType(String symbol, String interval, String alertType);
    
    List<PriceAlert> findBySymbolAndIntervalOrderByTdCountDesc(String symbol, String interval);
}