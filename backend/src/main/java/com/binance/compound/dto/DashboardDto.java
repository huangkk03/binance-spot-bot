package com.binance.compound.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class DashboardDto {
    private Map<String, BigDecimal> currentPrices;
    private Map<String, BigDecimal> totalPnL;
    private Integer totalInstances;
    private Integer openInstances;
    private BigDecimal totalQuoteBalance;
    private BigDecimal totalBaseBalance;
}
