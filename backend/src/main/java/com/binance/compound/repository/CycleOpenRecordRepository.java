package com.binance.compound.repository;

import com.binance.compound.entity.CycleOpenRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CycleOpenRecordRepository extends JpaRepository<CycleOpenRecord, Long> {
    
    List<CycleOpenRecord> findBySymbolAndInstanceIdAndIsSimulationOrderByOpenedAtUtcDesc(
            String symbol, Integer instanceId, Boolean isSimulation, Pageable pageable);
    
    List<CycleOpenRecord> findByIsSimulationOrderByOpenedAtUtcDesc(Boolean isSimulation, Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM CycleOpenRecord c WHERE c.isSimulation = :isSimulation")
    void deleteByIsSimulation(@Param("isSimulation") Boolean isSimulation);
}
