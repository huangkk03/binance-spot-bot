package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "instance_events",
        indexes = {
                @Index(name = "idx_symbol_instance_created", columnList = "symbol, instanceId, createdAtUtc"),
                @Index(name = "idx_event_type", columnList = "event, isSimulation")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanceEvent {
    
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
    
    @Column(nullable = false, length = 30)
    private String event;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal baseQty = BigDecimal.ZERO;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal quoteAmount = BigDecimal.ZERO;
    
    @Column(length = 500)
    @Builder.Default
    private String note = "";
    
    @Column(updatable = false)
    private LocalDateTime createdAtUtc;
    
    @PrePersist
    protected void onCreate() {
        createdAtUtc = LocalDateTime.now();
    }
}
