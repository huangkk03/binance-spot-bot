package com.binance.compound.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {
    
    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "binance-compound-pro",
                "timestamp", System.currentTimeMillis()
        );
    }
}
