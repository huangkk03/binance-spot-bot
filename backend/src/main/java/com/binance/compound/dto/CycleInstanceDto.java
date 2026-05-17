package com.binance.compound.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CycleInstanceDto {
    private Long id;
    private String symbol;
    private Integer instanceId;
    private Integer cycleId;
    private Boolean isSimulation;
    private Boolean isOpen;
    private BigDecimal anchorPrice;
    private BigDecimal reentryPrice;
    private BigDecimal cycleStartPrice;
    private BigDecimal lastActionPrice;
    private BigDecimal baseQty;
    private BigDecimal spentQuote;
    private BigDecimal quoteAmount;
    private BigDecimal cumulativeProfit;
    private LocalDateTime updatedAtUtc;
    private LocalDateTime createdAtUtc;
}
