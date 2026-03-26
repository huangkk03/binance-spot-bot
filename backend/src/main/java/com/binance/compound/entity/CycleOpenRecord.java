package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cycle_open_records",
        indexes = @Index(name = "idx_symbol_instance_sim", columnList = "symbol, instanceId, isSimulation"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleOpenRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false)
    private Integer instanceId;
    
    @Column(nullable = false)
    private Integer cycleId;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSimulation = true;
    
    @Column(precision = 32, scale = 16, nullable = false)
    private BigDecimal startPrice;
    
    @Column(precision = 32, scale = 16, nullable = false)
    private BigDecimal quoteAmount;
    
    @Column(nullable = false)
    private LocalDateTime openedAtUtc;
    
    @Column(updatable = false)
    private LocalDateTime createdAtUtc;
    
    @PrePersist
    protected void onCreate() {
        createdAtUtc = LocalDateTime.now();
    }
}
