package com.binance.compound.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyConfigDto {
    private String configKey;
    private String configValue;
    private Boolean isSimulation;
}
