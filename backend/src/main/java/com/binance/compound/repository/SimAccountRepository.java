package com.binance.compound.repository;

import com.binance.compound.entity.SimAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SimAccountRepository extends JpaRepository<SimAccount, Long> {
    
    Optional<SimAccount> findByAssetAndIsSimulation(String asset, Boolean isSimulation);
    
    @Modifying
    @Query("DELETE FROM SimAccount a WHERE a.isSimulation = :isSimulation")
    void deleteByIsSimulation(@Param("isSimulation") Boolean isSimulation);
}
