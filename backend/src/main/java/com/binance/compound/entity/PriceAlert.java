package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts",
        indexes = {
                @Index(name = "idx_symbol_interval_alert", columnList = "symbol, kline_interval, alert_type"),
                @Index(name = "idx_created_at", columnList = "created_at_utc")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "kline_interval", nullable = false, length = 10)
    private String interval;
    
    @Column(nullable = false, length = 20)
    private String alertType;
    
    @Column(nullable = false)
    private Integer tdCount;
    
    @Column(precision = 32, scale = 16, nullable = false)
    private BigDecimal currentPrice;
    
    @Column(precision = 32, scale = 16, nullable = false)
    private BigDecimal triggerPrice;
    
    @Column(nullable = false)
    private Boolean triggered;
    
    @Column(length = 500)
    private String message;
    
    @Column(nullable = false)
    private LocalDateTime createdAtUtc;
    
    @PrePersist
    protected void onCreate() {
        createdAtUtc = LocalDateTime.now();
    }
}
