package com.binance.compound.repository;

import com.binance.compound.entity.ApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiConfigRepository extends JpaRepository<ApiConfig, Long> {
    
    Optional<ApiConfig> findByConfigKey(String configKey);
}
