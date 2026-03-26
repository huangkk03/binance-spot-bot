package com.binance.compound.repository;

import com.binance.compound.entity.SimAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SimAccountRepository extends JpaRepository<SimAccount, Long> {
    
    Optional<SimAccount> findByAssetAndIsSimulation(String asset, Boolean isSimulation);
}
