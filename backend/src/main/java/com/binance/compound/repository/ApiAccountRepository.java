package com.binance.compound.repository;

import com.binance.compound.entity.ApiAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiAccountRepository extends JpaRepository<ApiAccount, Long> {
    
    List<ApiAccount> findAllByOrderByCreatedAtUtcDesc();
    
    Optional<ApiAccount> findByIsActiveTrue();
    
    @Modifying
    @Query("UPDATE ApiAccount a SET a.isActive = false")
    void deactivateAll();
    
    @Modifying
    @Query("UPDATE ApiAccount a SET a.isActive = true WHERE a.id = :id")
    void setActiveById(Long id);
}
