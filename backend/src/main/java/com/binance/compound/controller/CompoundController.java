package com.binance.compound.controller;

import com.binance.compound.dto.DepositRequest;
import com.binance.compound.dto.CycleInstanceDto;
import com.binance.compound.dto.SimAccountDto;
import com.binance.compound.dto.StrategyConfigDto;
import com.binance.compound.dto.InstanceEventDto;
import com.binance.compound.dto.PriceAlertDto;
import com.binance.compound.entity.InstanceEvent;
import com.binance.compound.entity.PriceAlert;
import com.binance.compound.entity.StrategyConfig;
import com.binance.compound.entity.CycleInstance;
import com.binance.compound.entity.SimAccount;
import com.binance.compound.entity.RrState;
import com.binance.compound.entity.ExpectedFree;
import com.binance.compound.entity.CycleOpenRecord;
import com.binance.compound.entity.TradeRecord;
import com.binance.compound.repository.InstanceEventRepository;
import com.binance.compound.repository.CycleInstanceRepository;
import com.binance.compound.repository.StrategyConfigRepository;
import com.binance.compound.repository.PriceAlertRepository;
import com.binance.compound.repository.SimAccountRepository;
import com.binance.compound.repository.RrStateRepository;
import com.binance.compound.repository.ExpectedFreeRepository;
import com.binance.compound.repository.CycleOpenRecordRepository;
import com.binance.compound.repository.TradeRecordRepository;
import com.binance.compound.service.SimAccountService;
import com.binance.compound.service.SimulationEngine;
import com.binance.compound.service.PriceService;
import com.binance.compound.service.TDScannerService;
import com.binance.compound.service.RealTradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
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
    private final TDScannerService tdScannerService;
    private final RealTradingService realTradingService;
    private final StrategyConfigRepository strategyConfigRepository;
    private final InstanceEventRepository instanceEventRepository;
    private final CycleInstanceRepository cycleInstanceRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final SimAccountRepository simAccountRepository;
    private final RrStateRepository rrStateRepository;
    private final ExpectedFreeRepository expectedFreeRepository;
    private final CycleOpenRecordRepository cycleOpenRecordRepository;
    private final TradeRecordRepository tradeRecordRepository;
    
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
        Boolean isSimulation = config.getIsSimulation() != null ? config.getIsSimulation() : true;
        StrategyConfig strategyConfig = strategyConfigRepository.findByConfigKeyAndIsSimulation(key, isSimulation)
                .orElseGet(() -> StrategyConfig.builder()
                        .configKey(key)
                        .isSimulation(isSimulation)
                        .configValue(config.getConfigValue())
                        .build());
        strategyConfig.setConfigValue(config.getConfigValue());
        strategyConfigRepository.save(strategyConfig);
        return Map.of("success", true, "key", key, "value", config.getConfigValue());
    }
    
    @GetMapping("/mode")
    public Map<String, Object> getCurrentMode() {
        Boolean isSimulation = strategyConfigRepository.findByConfigKeyAndIsSimulation("CURRENT_MODE", false)
                .map(c -> "true".equals(c.getConfigValue()))
                .orElse(true);
        return Map.of("isSimulation", isSimulation);
    }
    
    @PutMapping("/mode")
    public Map<String, Object> setCurrentMode(@RequestBody Map<String, Boolean> body) {
        Boolean isSimulation = body.get("isSimulation");
        if (isSimulation == null) {
            isSimulation = true;
        }
        StrategyConfig config = strategyConfigRepository.findByConfigKeyAndIsSimulation("CURRENT_MODE", false)
                .orElse(StrategyConfig.builder()
                        .configKey("CURRENT_MODE")
                        .isSimulation(false)
                        .configValue("true")
                        .build());
        config.setConfigValue(isSimulation ? "true" : "false");
        strategyConfigRepository.save(config);
        return Map.of("success", true, "isSimulation", isSimulation);
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
    
    @GetMapping("/alerts")
    public List<PriceAlertDto> getAlerts(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String interval) {
        List<PriceAlert> alerts;
        if (symbol != null && interval != null) {
            alerts = priceAlertRepository.findBySymbolAndInterval(symbol, interval);
        } else {
            alerts = priceAlertRepository.findAll();
        }
        return alerts.stream()
                .map(a -> PriceAlertDto.builder()
                        .id(a.getId())
                        .symbol(a.getSymbol())
                        .interval(a.getInterval())
                        .alertType(a.getAlertType())
                        .tdCount(a.getTdCount())
                        .currentPrice(a.getCurrentPrice())
                        .triggerPrice(a.getTriggerPrice())
                        .triggered(a.getTriggered())
                        .message(a.getMessage())
                        .createdAtUtc(a.getCreatedAtUtc())
                        .build())
                .toList();
    }
    
    @GetMapping("/alerts/triggered")
    public List<PriceAlertDto> getTriggeredAlerts() {
        return priceAlertRepository.findByTriggered(true).stream()
                .map(a -> PriceAlertDto.builder()
                        .id(a.getId())
                        .symbol(a.getSymbol())
                        .interval(a.getInterval())
                        .alertType(a.getAlertType())
                        .tdCount(a.getTdCount())
                        .currentPrice(a.getCurrentPrice())
                        .triggerPrice(a.getTriggerPrice())
                        .triggered(a.getTriggered())
                        .message(a.getMessage())
                        .createdAtUtc(a.getCreatedAtUtc())
                        .build())
                .toList();
    }
    
    @PostMapping("/alerts/scan")
    public Map<String, Object> triggerScan() {
        tdScannerService.scanTDIndicators();
        return Map.of("success", true, "message", "TD scan triggered");
    }
    
    @PostMapping("/real-tick")
    public Map<String, Object> executeRealTick(
            @RequestBody List<String> symbols,
            @RequestParam(required = false) BigDecimal quoteAmount) {
        return realTradingService.executeRealTick(symbols, quoteAmount);
    }
    
    @PostMapping("/simulation/clear")
    @Transactional
    public Map<String, Object> clearSimulationData() {
        try {
            Boolean isSimulation = true;
            
            instanceEventRepository.deleteByIsSimulation(isSimulation);
            cycleOpenRecordRepository.deleteByIsSimulation(isSimulation);
            cycleInstanceRepository.deleteByIsSimulation(isSimulation);
            tradeRecordRepository.deleteByIsSimulation(isSimulation);
            simAccountRepository.deleteByIsSimulation(isSimulation);
            rrStateRepository.deleteByIsSimulation(isSimulation);
            expectedFreeRepository.deleteByIsSimulation(isSimulation);
            priceAlertRepository.deleteAll();
            
            log.info("Cleared all simulation data");
            
            return Map.of(
                    "success", true,
                    "message", "All simulation data cleared"
            );
        } catch (Exception e) {
            log.error("Failed to clear simulation data: {}", e.getMessage());
            return Map.of(
                    "success", false,
                    "message", "Failed to clear data: " + e.getMessage()
            );
        }
    }
}
