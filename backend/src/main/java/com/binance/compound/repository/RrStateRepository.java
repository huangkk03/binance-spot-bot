package com.binance.compound.repository;

import com.binance.compound.entity.RrState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RrStateRepository extends JpaRepository<RrState, Long> {
    
    Optional<RrState> findByQuoteAssetAndIsSimulation(String quoteAsset, Boolean isSimulation);
}
