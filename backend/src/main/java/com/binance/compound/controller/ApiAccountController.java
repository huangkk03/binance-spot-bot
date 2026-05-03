package com.binance.compound.controller;

import com.binance.compound.dto.ApiAccountDto;
import com.binance.compound.entity.ApiAccount;
import com.binance.compound.repository.ApiAccountRepository;
import com.binance.compound.service.BinanceApiService;
import com.binance.compound.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/api-accounts")
@RequiredArgsConstructor
public class ApiAccountController {
    
    private final ApiAccountRepository apiAccountRepository;
    private final BinanceApiService binanceApiService;
    
    @GetMapping
    public List<ApiAccountDto> getAllAccounts() {
        return apiAccountRepository.findAllByOrderByCreatedAtUtcDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    @GetMapping("/active")
    public Map<String, Object> getActiveAccount() {
        ApiAccount active = apiAccountRepository.findByIsActiveTrue().orElse(null);
        if (active == null) {
            return Map.of("hasActive", false);
        }
        return Map.of(
            "hasActive", true,
            "account", toDto(active)
        );
    }
    
    @PostMapping
    public ApiAccountDto createAccount(@RequestBody ApiAccountDto dto) {
        ApiAccount account = ApiAccount.builder()
                .accountName(dto.getAccountName())
                .apiKey(dto.getApiKey())
                .apiSecret(EncryptionUtil.encrypt(dto.getApiSecret()))
                .useProxy(dto.getUseProxy() != null ? dto.getUseProxy() : false)
                .proxyUrl(dto.getProxyUrl() != null ? dto.getProxyUrl() : "")
                .testnet(dto.getTestnet() != null ? dto.getTestnet() : true)
                .isActive(false)
                .build();
        
        ApiAccount saved = apiAccountRepository.save(account);
        log.info("Created API account: {}", saved.getAccountName());
        return toDto(saved);
    }
    
    @PutMapping("/{id}")
    public ApiAccountDto updateAccount(@PathVariable Long id, @RequestBody ApiAccountDto dto) {
        ApiAccount account = apiAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        
        if (dto.getAccountName() != null) {
            account.setAccountName(dto.getAccountName());
        }
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty() && !dto.getApiKey().contains("****")) {
            account.setApiKey(dto.getApiKey());
        }
        if (dto.getApiSecret() != null && !dto.getApiSecret().isEmpty() && !dto.getApiSecret().contains("****")) {
            account.setApiSecret(EncryptionUtil.encrypt(dto.getApiSecret()));
        }
        if (dto.getUseProxy() != null) {
            account.setUseProxy(dto.getUseProxy());
        }
        if (dto.getProxyUrl() != null) {
            account.setProxyUrl(dto.getProxyUrl());
        }
        if (dto.getTestnet() != null) {
            account.setTestnet(dto.getTestnet());
        }
        
        ApiAccount saved = apiAccountRepository.save(account);
        log.info("Updated API account: {}", saved.getAccountName());
        return toDto(saved);
    }
    
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAccount(@PathVariable Long id) {
        ApiAccount account = apiAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        
        apiAccountRepository.delete(account);
        log.info("Deleted API account: {}", account.getAccountName());
        return Map.of("success", true, "id", id);
    }
    
    @Transactional
    @PostMapping("/{id}/activate")
    public Map<String, Object> activateAccount(@PathVariable Long id) {
        ApiAccount account = apiAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        
        apiAccountRepository.deactivateAll();
        apiAccountRepository.setActiveById(id);
        
        log.info("Activated API account: {}", account.getAccountName());
        return Map.of("success", true, "id", id, "accountName", account.getAccountName());
    }
    
    @PostMapping("/test")
    public Map<String, Object> testAccount(@RequestBody Map<String, Object> body) {
        String apiKey = (String) body.get("apiKey");
        String apiSecret = (String) body.get("apiSecret");
        Boolean testnet = body.get("testnet") != null ? Boolean.parseBoolean(body.get("testnet").toString()) : false;
        String proxyUrl = body.get("proxyUrl") != null ? (String) body.get("proxyUrl") : "";
        
        return binanceApiService.testApiConnection(apiKey, apiSecret, testnet, proxyUrl);
    }
    
    @GetMapping("/{id}/balances")
    public Map<String, Object> getAccountBalances(@PathVariable Long id) {
        ApiAccount account = apiAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        
        return binanceApiService.getAccountBalances(
                account.getApiKey(),
                EncryptionUtil.decrypt(account.getApiSecret()),
                account.getTestnet(),
                account.getUseProxy() ? account.getProxyUrl() : ""
        );
    }
    
    private ApiAccountDto toDto(ApiAccount account) {
        return ApiAccountDto.builder()
                .id(account.getId())
                .accountName(account.getAccountName())
                .apiKey(account.getApiKey()) // Return real apiKey
                .apiSecret("********") // Mask secret completely
                .maskedApiKey(mask(account.getApiKey()))
                .maskedApiSecret("********")
                .useProxy(account.getUseProxy())
                .proxyUrl(account.getProxyUrl())
                .testnet(account.getTestnet())
                .isActive(account.getIsActive())
                .build();
    }
    
    private String mask(String value) {
        if (value == null || value.length() < 8) {
            return "******";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
