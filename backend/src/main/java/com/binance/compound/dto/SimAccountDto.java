package com.binance.compound.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SimAccountDto {
    private Long id;
    private String asset;
    private BigDecimal freeBalance;
    private BigDecimal lockedBalance;
    private Boolean isSimulation;
    private LocalDateTime updatedAtUtc;
}
