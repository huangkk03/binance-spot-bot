package com.binance.compound.controller;

import com.binance.compound.dto.DepositRequest;
import com.binance.compound.dto.CycleInstanceDto;
import com.binance.compound.dto.SimAccountDto;
import com.binance.compound.dto.StrategyConfigDto;
import com.binance.compound.dto.InstanceEventDto;
import com.binance.compound.entity.InstanceEvent;
import com.binance.compound.entity.StrategyConfig;
import com.binance.compound.entity.CycleInstance;
import com.binance.compound.repository.InstanceEventRepository;
import com.binance.compound.repository.CycleInstanceRepository;
import com.binance.compound.repository.StrategyConfigRepository;
import com.binance.compound.service.SimAccountService;
import com.binance.compound.service.SimulationEngine;
import com.binance.compound.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CompoundController {
    
    private final SimulationEngine simulationEngine;
    private final SimAccountService simAccountService;
    private final PriceService priceService;
    private final StrategyConfigRepository strategyConfigRepository;
    private final InstanceEventRepository instanceEventRepository;
    private final CycleInstanceRepository cycleInstanceRepository;
    
    @PostMapping("/deposit")
    public Map<String, Object> deposit(@RequestBody DepositRequest request) {
        simAccountService.deposit(
                request.getAsset() != null ? request.getAsset() : "USDT",
                request.getAmount(),
                request.getIsSimulation() != null ? request.getIsSimulation() : true
        );
        
        return Map.of(
                "success", true,
                "message", "Deposited " + request.getAmount() + " " + request.getAsset()
        );
    }
    
    @GetMapping("/accounts/{asset}")
    public SimAccountDto getAccount(
            @PathVariable String asset,
            @RequestParam(defaultValue = "true") Boolean isSimulation) {
        var account = simAccountService.getAccount(asset, isSimulation);
        if (account == null) {
            return SimAccountDto.builder()
                    .asset(asset)
                    .freeBalance(BigDecimal.ZERO)
                    .lockedBalance(BigDecimal.ZERO)
                    .isSimulation(isSimulation)
                    .build();
        }
        return SimAccountDto.builder()
                .id(account.getId())
                .asset(account.getAsset())
                .freeBalance(account.getFreeBalance())
                .lockedBalance(account.getLockedBalance())
                .isSimulation(account.getIsSimulation())
                .updatedAtUtc(account.getUpdatedAtUtc())
                .build();
    }
    
    @PostMapping("/tick")
    public Map<String, Object> executeTick(
            @RequestBody List<String> symbols,
            @RequestParam(defaultValue = "true") Boolean isSimulation) {
        List<String> actions = simulationEngine.executeTick(symbols, isSimulation);
        return Map.of(
                "success", true,
                "actions", actions
        );
    }
    
    @GetMapping("/instances")
    public List<CycleInstanceDto> getInstances(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "true") Boolean isSimulation) {
        if (symbol != null && !symbol.isEmpty()) {
            return simulationEngine.getInstancesBySymbol(symbol, isSimulation);
        }
        return simulationEngine.getAllInstances(isSimulation);
    }
    
    @GetMapping("/prices")
    public Map<String, BigDecimal> getPrices() {
        return priceService.getAllPrices();
    }
    
    @GetMapping("/prices/{symbol}")
    public BigDecimal getPrice(@PathVariable String symbol) {
        return priceService.getPrice(symbol);
    }
    
    @GetMapping("/kline/{symbol}")
    public Map<String, Object> getKLine(@PathVariable String symbol) {
        PriceService.KLineData kline = priceService.getKLine(symbol);
        if (kline != null) {
            return Map.of(
                    "success", true,
                    "symbol", symbol,
                    "open", kline.getOpen(),
                    "high", kline.getHigh(),
                    "low", kline.getLow(),
                    "close", kline.getClose(),
                    "volume", kline.getVolume(),
                    "openTime", kline.getOpenTime(),
                    "closeTime", kline.getCloseTime()
            );
        }
        return Map.of("success", false, "message", "Failed to fetch kline data");
    }
    
    @PostMapping("/prices/subscribe/{symbol}")
    public Map<String, Object> subscribePrice(@PathVariable String symbol) {
        priceService.subscribe(symbol);
        return Map.of("success", true, "symbol", symbol);
    }
    
    @GetMapping("/config")
    public List<StrategyConfigDto> getConfig(@RequestParam(defaultValue = "true") Boolean isSimulation) {
        List<StrategyConfig> configs = strategyConfigRepository.findByIsSimulation(isSimulation);
        return configs.stream()
                .map(c -> StrategyConfigDto.builder()
                        .configKey(c.getConfigKey())
                        .configValue(c.getConfigValue())
                        .isSimulation(c.getIsSimulation())
                        .build())
                .toList();
    }
    
    @PutMapping("/config/{key}")
    public Map<String, Object> updateConfig(
            @PathVariable String key,
            @RequestBody StrategyConfigDto config) {
        strategyConfigRepository.findByConfigKeyAndIsSimulation(key, config.getIsSimulation() != null ? config.getIsSimulation() : true)
                .ifPresent(existing -> {
                    existing.setConfigValue(config.getConfigValue());
                    strategyConfigRepository.save(existing);
                });
        return Map.of("success", true, "key", key, "value", config.getConfigValue());
    }
    
    @GetMapping("/history/events")
    public List<InstanceEventDto> getEventHistory(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "true") Boolean isSimulation,
            @RequestParam(defaultValue = "100") Integer limit) {
        List<InstanceEvent> events;
        if (symbol != null && !symbol.isEmpty()) {
            events = instanceEventRepository.findBySymbolAndIsSimulationOrderByCreatedAtUtcDesc(
                    symbol, isSimulation, PageRequest.of(0, limit));
        } else {
            events = instanceEventRepository.findByIsSimulationOrderByCreatedAtUtcDesc(
                    isSimulation, PageRequest.of(0, limit));
        }
        return events.stream()
                .map(e -> InstanceEventDto.builder()
                        .id(e.getId())
                        .symbol(e.getSymbol())
                        .instanceId(e.getInstanceId())
                        .cycleId(e.getCycleId())
                        .event(e.getEvent())
                        .price(e.getPrice())
                        .baseQty(e.getBaseQty())
                        .quoteAmount(e.getQuoteAmount())
                        .note(e.getNote())
                        .createdAtUtc(e.getCreatedAtUtc())
                        .build())
                .toList();
    }
    
    @GetMapping("/history/orders")
    public List<CycleInstanceDto> getOrderHistory(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "true") Boolean isSimulation,
            @RequestParam(defaultValue = "100") Integer limit) {
        List<CycleInstance> instances;
        if (symbol != null && !symbol.isEmpty()) {
            instances = cycleInstanceRepository.findBySymbolAndIsSimulationOrderByInstanceIdDesc(
                    symbol, isSimulation, PageRequest.of(0, limit));
        } else {
            instances = cycleInstanceRepository.findByIsSimulationOrderByIdDesc(
                    isSimulation, PageRequest.of(0, limit));
        }
        return instances.stream()
                .map(i -> CycleInstanceDto.builder()
                        .id(i.getId())
                        .symbol(i.getSymbol())
                        .instanceId(i.getInstanceId())
                        .cycleId(i.getCycleId())
                        .isSimulation(i.getIsSimulation())
                        .isOpen(i.getIsOpen())
                        .anchorPrice(i.getAnchorPrice())
                        .reentryPrice(i.getReentryPrice())
                        .cycleStartPrice(i.getCycleStartPrice())
                        .lastActionPrice(i.getLastActionPrice())
                        .baseQty(i.getBaseQty())
                        .spentQuote(i.getSpentQuote())
                        .quoteAmount(i.getQuoteAmount())
                        .updatedAtUtc(i.getUpdatedAtUtc())
                        .createdAtUtc(i.getCreatedAtUtc())
                        .build())
                .toList();
    }
}
