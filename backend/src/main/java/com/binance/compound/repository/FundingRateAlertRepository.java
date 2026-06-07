package com.binance.compound.repository;

import com.binance.compound.entity.FundingRateAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FundingRateAlertRepository extends JpaRepository<FundingRateAlert, Long> {

    Optional<FundingRateAlert> findBySymbolAndAlertType(String symbol, String alertType);

    List<FundingRateAlert> findBySymbol(String symbol);
}
