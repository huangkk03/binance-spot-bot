package com.binance.compound.repository;

import com.binance.compound.entity.InstanceEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstanceEventRepository extends JpaRepository<InstanceEvent, Long> {
    
    List<InstanceEvent> findBySymbolAndInstanceIdAndIsSimulationOrderByCreatedAtUtcDesc(
            String symbol, Integer instanceId, Boolean isSimulation, Pageable pageable);
    
    List<InstanceEvent> findBySymbolAndIsSimulationOrderByCreatedAtUtcDesc(
            String symbol, Boolean isSimulation, Pageable pageable);
    
    @Query("SELECT e.instanceId, COUNT(e) FROM InstanceEvent e " +
           "WHERE e.symbol = :symbol AND e.event IN ('SELL_STEP', 'SELL_BUY') AND e.isSimulation = :isSimulation " +
           "GROUP BY e.instanceId")
    List<Object[]> countSellStepByInstanceId(@Param("symbol") String symbol, @Param("isSimulation") Boolean isSimulation);
    
    List<InstanceEvent> findByIsSimulationOrderByCreatedAtUtcDesc(Boolean isSimulation, Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM InstanceEvent e WHERE e.isSimulation = :isSimulation")
    void deleteByIsSimulation(@Param("isSimulation") Boolean isSimulation);
}
