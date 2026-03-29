package com.binance.compound.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiAccountDto {
    private Long id;
    private String accountName;
    private String apiKey;
    private String apiSecret;
    private String maskedApiKey;
    private String maskedApiSecret;
    private Boolean useProxy;
    private String proxyUrl;
    private Boolean testnet;
    private Boolean isActive;
}
