package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_records",
        indexes = {
                @Index(name = "idx_symbol_sim_created", columnList = "symbol, isSimulation, createdAtUtc"),
                @Index(name = "idx_order_id", columnList = "orderId")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String orderId;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false, length = 10)
    private String side;
    
    @Column(nullable = false, length = 20)
    private String status;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSimulation = true;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal executedQty = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal cummulativeQuoteQty = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal avgPrice = BigDecimal.ZERO;
    
    @Column(columnDefinition = "TEXT")
    private String payloadJson;
    
    @Column(updatable = false)
    private LocalDateTime createdAtUtc;
    
    @PrePersist
    protected void onCreate() {
        createdAtUtc = LocalDateTime.now();
    }
}
