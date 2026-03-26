package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cycle_instances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "instance_id", "is_simulation"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleInstance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false)
    private Integer instanceId;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer cycleId = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSimulation = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isOpen = false;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal anchorPrice = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal reentryPrice = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal cycleStartPrice = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal lastActionPrice = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal baseQty = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal spentQuote = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal quoteAmount = BigDecimal.ZERO;
    
    @Column(updatable = false)
    private LocalDateTime createdAtUtc;
    
    private LocalDateTime updatedAtUtc;
    
    @PrePersist
    protected void onCreate() {
        createdAtUtc = LocalDateTime.now();
        updatedAtUtc = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAtUtc = LocalDateTime.now();
    }
}
