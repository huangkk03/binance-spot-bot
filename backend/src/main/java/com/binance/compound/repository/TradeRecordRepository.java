package com.binance.compound.repository;

import com.binance.compound.entity.TradeRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {
    
    List<TradeRecord> findBySymbolAndIsSimulationOrderByCreatedAtUtcDesc(String symbol, Boolean isSimulation, Pageable pageable);
    
    List<TradeRecord> findByIsSimulationOrderByCreatedAtUtcDesc(Boolean isSimulation, Pageable pageable);
}
