package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "rr_state",
        uniqueConstraints = @UniqueConstraint(columnNames = {"quoteAsset", "isSimulation"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RrState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String quoteAsset;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String lastSymbol = "";
    
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
