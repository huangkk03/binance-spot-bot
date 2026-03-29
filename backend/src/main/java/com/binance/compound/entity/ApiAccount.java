package com.binance.compound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "account_name", nullable = false, length = 50)
    private String accountName;
    
    @Column(name = "api_key", columnDefinition = "TEXT", nullable = false)
    private String apiKey;
    
    @Column(name = "api_secret", columnDefinition = "TEXT", nullable = false)
    private String apiSecret;
    
    @Column(name = "use_proxy", nullable = false)
    private Boolean useProxy = false;
    
    @Column(name = "proxy_url", length = 200)
    private String proxyUrl = "";
    
    @Column(name = "testnet", nullable = false)
    private Boolean testnet = true;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;
    
    @Column(name = "created_at_utc")
    private LocalDateTime createdAtUtc;
    
    @Column(name = "updated_at_utc")
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
