package com.binance.compound.controller;

import com.binance.compound.entity.ApiConfig;
import com.binance.compound.repository.ApiConfigRepository;
import com.binance.compound.service.AiPredictionService;
import com.binance.compound.service.BinanceApiService;
import com.binance.compound.service.NotificationService;
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
    private final AiPredictionService aiPredictionService;
    private final NotificationService notificationService;
    
    @GetMapping("/{key}")
    public Map<String, Object> getApiConfig(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        
        return apiConfigRepository.findByConfigKey(key)
                .map(config -> {
                    boolean isSecret = key.toLowerCase().contains("secret") || key.toLowerCase().contains("key");
                    result.put("value", isSecret && config.getConfigValue() != null ? "******" : config.getConfigValue());
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
    
    @PostMapping("/test-ai")
    public Map<String, Object> testAiConfig(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String key = body.get("key");
        String model = body.get("model");
        
        return aiPredictionService.testAiConnection(url, key, model);
    }

    @PostMapping("/test-notification")
    public Map<String, Object> testNotification(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String title = body.getOrDefault("title", "通知测试");
            String content = body.getOrDefault("content", "这是一条测试通知，用于验证微信和邮件配置是否可用。");
            notificationService.sendWeChatNotification(title, content);
            notificationService.sendEmailNotification(title, content);
            result.put("success", true);
            result.put("message", "通知测试已发送（如果已配置渠道）");
        } catch (Exception e) {
            log.error("Test notification failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
