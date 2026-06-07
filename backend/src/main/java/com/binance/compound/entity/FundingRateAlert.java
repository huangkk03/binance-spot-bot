package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "funding_rate_alerts",
        indexes = {
                @Index(name = "idx_last_notified", columnList = "last_notified_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundingRateAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "alert_type", nullable = false, length = 20)
    private String alertType;

    @Column(precision = 32, scale = 16)
    private BigDecimal fundingRate;

    @Column(precision = 32, scale = 16)
    private BigDecimal annualizedRate;

    @Column(name = "next_funding_time")
    private Long nextFundingTime;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private LocalDateTime createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false)
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
