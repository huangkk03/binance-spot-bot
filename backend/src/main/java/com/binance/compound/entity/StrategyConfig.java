package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"configKey", "isSimulation"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String configKey;
    
    @Column(nullable = false, length = 200)
    private String configValue;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSimulation = true;
    
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
