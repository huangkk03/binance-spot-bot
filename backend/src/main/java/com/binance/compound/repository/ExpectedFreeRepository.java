package com.binance.compound.repository;

import com.binance.compound.entity.ExpectedFree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpectedFreeRepository extends JpaRepository<ExpectedFree, Long> {
    
    Optional<ExpectedFree> findByAssetAndIsSimulation(String asset, Boolean isSimulation);
    
    @Modifying
    @Query("DELETE FROM ExpectedFree e WHERE e.isSimulation = :isSimulation")
    void deleteByIsSimulation(@Param("isSimulation") Boolean isSimulation);
}
