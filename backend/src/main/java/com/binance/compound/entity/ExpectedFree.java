package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expected_free",
        uniqueConstraints = @UniqueConstraint(columnNames = {"asset", "isSimulation"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedFree {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String asset;
    
    @Column(precision = 32, scale = 16, nullable = false)
    @Builder.Default
    private BigDecimal expectedFree = BigDecimal.ZERO;
    
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
