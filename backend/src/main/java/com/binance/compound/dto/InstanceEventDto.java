package com.binance.compound.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanceEventDto {
    private Long id;
    private String symbol;
    private Integer instanceId;
    private Integer cycleId;
    private String event;
    private BigDecimal price;
    private BigDecimal baseQty;
    private BigDecimal quoteAmount;
    private String note;
    private LocalDateTime createdAtUtc;
}
