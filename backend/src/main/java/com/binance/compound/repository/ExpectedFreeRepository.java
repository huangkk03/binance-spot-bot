package com.binance.compound.repository;

import com.binance.compound.entity.ExpectedFree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpectedFreeRepository extends JpaRepository<ExpectedFree, Long> {
    
    Optional<ExpectedFree> findByAssetAndIsSimulation(String asset, Boolean isSimulation);
}
