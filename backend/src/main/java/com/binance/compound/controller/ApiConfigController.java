package com.binance.compound.controller;

import com.binance.compound.entity.ApiConfig;
import com.binance.compound.repository.ApiConfigRepository;
import com.binance.compound.service.BinanceApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/api-config")
@RequiredArgsConstructor
public class ApiConfigController {
    
    private final ApiConfigRepository apiConfigRepository;
    private final BinanceApiService binanceApiService;
    
    @GetMapping("/{key}")
    public Map<String, Object> getApiConfig(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        
        return apiConfigRepository.findByConfigKey(key)
                .map(config -> {
                    result.put("value", config.getConfigValue() != null ? "******" : null);
                    result.put("hasValue", config.getConfigValue() != null && !config.getConfigValue().isEmpty());
                    return result;
                })
                .orElseGet(() -> {
                    result.put("value", null);
                    result.put("hasValue", false);
                    return result;
                });
    }
    
    @PutMapping("/{key}")
    public Map<String, Object> saveApiConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        
        ApiConfig config = apiConfigRepository.findByConfigKey(key)
                .orElse(ApiConfig.builder()
                        .configKey(key)
                        .build());
        
        config.setConfigValue(value);
        config.setUpdatedAtUtc(LocalDateTime.now());
        apiConfigRepository.save(config);
        
        log.info("API config updated: {}", key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("key", key);
        result.put("hasValue", value != null && !value.isEmpty());
        return result;
    }
    
    @DeleteMapping("/{key}")
    public Map<String, Object> deleteApiConfig(@PathVariable String key) {
        apiConfigRepository.findByConfigKey(key).ifPresent(apiConfigRepository::delete);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("key", key);
        return result;
    }
    
    @PostMapping("/test")
    public Map<String, Object> testApiConfig(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        String apiSecret = body.get("apiSecret");
        Boolean testnet = body.get("testnet") != null ? Boolean.parseBoolean(body.get("testnet")) : false;
        String proxyUrl = body.get("proxyUrl");
        
        return binanceApiService.testApiConnection(apiKey, apiSecret, testnet, proxyUrl);
    }
}
