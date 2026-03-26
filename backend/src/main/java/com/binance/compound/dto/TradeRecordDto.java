package com.binance.compound.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TradeRecordDto {
    private Long id;
    private String orderId;
    private String symbol;
    private String side;
    private String status;
    private Boolean isSimulation;
    private BigDecimal executedQty;
    private BigDecimal cummulativeQuoteQty;
    private BigDecimal avgPrice;
    private String payloadJson;
    private LocalDateTime createdAtUtc;
}
