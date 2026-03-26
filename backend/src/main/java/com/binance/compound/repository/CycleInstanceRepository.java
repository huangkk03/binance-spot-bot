package com.binance.compound.repository;

import com.binance.compound.entity.CycleInstance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CycleInstanceRepository extends JpaRepository<CycleInstance, Long> {
    
    List<CycleInstance> findBySymbolAndIsSimulationOrderByInstanceIdAsc(String symbol, Boolean isSimulation);
    
    List<CycleInstance> findByIsSimulationOrderBySymbolAscInstanceIdAsc(Boolean isSimulation);
    
    List<CycleInstance> findBySymbolAndIsSimulationOrderByInstanceIdDesc(String symbol, Boolean isSimulation, Pageable pageable);
    
    List<CycleInstance> findByIsSimulationOrderByIdDesc(Boolean isSimulation, Pageable pageable);
    
    @Query("SELECT COALESCE(MAX(c.instanceId), -1) + 1 FROM CycleInstance c WHERE c.symbol = :symbol AND c.isSimulation = :isSimulation")
    Integer findNextInstanceId(@Param("symbol") String symbol, @Param("isSimulation") Boolean isSimulation);
    
    @Query("SELECT COUNT(c) FROM CycleInstance c WHERE c.symbol = :symbol AND c.isSimulation = :isSimulation")
    Long countBySymbol(@Param("symbol") String symbol, @Param("isSimulation") Boolean isSimulation);
    
    @Query("SELECT SUM(c.baseQty) FROM CycleInstance c WHERE c.symbol = :symbol AND c.isOpen = true AND c.isSimulation = :isSimulation")
    BigDecimal sumBaseQtyBySymbolAndIsOpen(@Param("symbol") String symbol, @Param("isSimulation") Boolean isSimulation);
}
