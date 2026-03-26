package com.binance.compound.repository;

import com.binance.compound.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    
    Optional<StrategyConfig> findByConfigKeyAndIsSimulation(String configKey, Boolean isSimulation);
    
    List<StrategyConfig> findByIsSimulation(Boolean isSimulation);
}
