package com.binance.compound.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PriceAlertDto {
    private Long id;
    private String symbol;
    private String interval;
    private String alertType;
    private Integer tdCount;
    private BigDecimal currentPrice;
    private BigDecimal triggerPrice;
    private Boolean triggered;
    private String message;
    private LocalDateTime createdAtUtc;
}