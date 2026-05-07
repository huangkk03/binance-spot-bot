package com.binance.compound.service;

import com.binance.compound.dto.CycleInstanceDto;
import com.binance.compound.entity.*;
import com.binance.compound.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationEngine {
    
    private final CycleInstanceRepository cycleInstanceRepository;
    private final SimAccountRepository simAccountRepository;
    private final ExpectedFreeRepository expectedFreeRepository;
    private final RrStateRepository rrStateRepository;
    private final InstanceEventRepository instanceEventRepository;
    private final CycleOpenRecordRepository cycleOpenRecordRepository;
    private final StrategyConfigRepository strategyConfigRepository;
    private final PriceService priceService;
    private final NotificationService notificationService;
    
    @Value("${simulation.default-take-profit-pct:0.03}")
    private BigDecimal defaultTakeProfitPct;
    
    @Value("${simulation.default-stop-loss-pct:0.10}")
    private BigDecimal defaultStopLossPct;
    
    @Value("${simulation.default-quote-reserve:10}")
    private BigDecimal defaultQuoteReserve;
    
    @Value("${simulation.default-max-orders-per-tick:5}")
    private Integer defaultMaxOrdersPerTick;
    
    private static final String EVENT_DEPOSIT_ALLOC = "DEPOSIT_ALLOC";
    private static final String EVENT_BUY_OPEN = "BUY_OPEN";
    private static final String EVENT_TAKE_PROFIT = "TAKE_PROFIT";
    private static final String EVENT_REBUY_COMPOUND = "REBUY_COMPOUND";
    private static final String EVENT_WAIT_REENTRY = "WAIT_REENTRY";
    
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");
    private static final BigDecimal TAKER_FEE_RATE = new BigDecimal("0.001");
    
    private static final List<String> DEFAULT_SYMBOLS = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "DOGEUSDT", "SOLUSDT");
    
    @Value("${simulation.auto-tick-enabled:true}")
    private Boolean autoTickEnabled;
    
    @Value("${simulation.auto-tick-interval-ms:30000}")
    private Long autoTickIntervalMs;
    
    @Transactional
    public List<String> executeTick(List<String> symbols, Boolean isSimulation) {
        List<String> actions = new ArrayList<>();
        
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String symbol : symbols) {
            BigDecimal price = priceService.getPrice(symbol);
            prices.put(symbol, price);
        }
        
        Map<String, BigDecimal> depositByAsset = detectDeposits(symbols, isSimulation);
        
        for (Map.Entry<String, BigDecimal> entry : depositByAsset.entrySet()) {
            String quoteAsset = entry.getKey();
            BigDecimal delta = entry.getValue();
            
            String chosenSymbol = selectSymbolForDeposit(quoteAsset, symbols, isSimulation);
            if (chosenSymbol == null) continue;
            
            Integer nextInstanceId = cycleInstanceRepository.findNextInstanceId(chosenSymbol, isSimulation);
            
            CycleInstance inst = CycleInstance.builder()
                    .symbol(chosenSymbol)
                    .instanceId(nextInstanceId)
                    .cycleId(0)
                    .isSimulation(isSimulation)
                    .isOpen(false)
                    .anchorPrice(BigDecimal.ZERO)
                    .reentryPrice(BigDecimal.ZERO)
                    .cycleStartPrice(BigDecimal.ZERO)
                    .lastActionPrice(BigDecimal.ZERO)
                    .baseQty(BigDecimal.ZERO)
                    .spentQuote(BigDecimal.ZERO)
                    .quoteAmount(delta)
                    .build();
            
            cycleInstanceRepository.save(inst);
            
            recordEvent(chosenSymbol, nextInstanceId, 0, EVENT_DEPOSIT_ALLOC, 
                    BigDecimal.ZERO, BigDecimal.ZERO, delta, 
                    "detect_deposit " + quoteAsset + " delta=" + delta, isSimulation);
            
            RrState rrState = rrStateRepository.findByQuoteAssetAndIsSimulation(quoteAsset, isSimulation)
                    .orElseGet(() -> {
                        RrState newState = RrState.builder()
                                .quoteAsset(quoteAsset)
                                .lastSymbol("")
                                .isSimulation(isSimulation)
                                .build();
                        return rrStateRepository.save(newState);
                    });
            rrState.setLastSymbol(chosenSymbol);
            rrStateRepository.save(rrState);
            
            actions.add(String.format("DEPOSIT_ALLOC: %s allocated to %s instance#%d", 
                    delta, chosenSymbol, nextInstanceId));
        }
        
        int remainingOrders = getMaxOrdersPerTick("GLOBAL", isSimulation);
        
        for (String symbol : symbols) {
            if (remainingOrders <= 0) break;
            
            BigDecimal price = prices.get(symbol);
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            String quoteAsset = "USDT";
            BigDecimal spendableQuote = getSpendableQuote(symbol, quoteAsset, isSimulation);
            
            List<CycleInstance> instances = cycleInstanceRepository
                    .findBySymbolAndIsSimulationOrderByInstanceIdAsc(symbol, isSimulation);
            
            for (CycleInstance inst : instances) {
                if (remainingOrders <= 0) break;
                
                if (!inst.getIsOpen()) {
                    String result = tryOpenPosition(inst, price, spendableQuote, isSimulation);
                    if (result != null) {
                        actions.add(result);
                        remainingOrders--;
                    }
                } else {
                    String result = tryTakeProfitOrRebuy(inst, price, spendableQuote, isSimulation);
                    if (result != null) {
                        actions.add(result);
                        remainingOrders--;
                    }
                }
            }
        }
        
        updateExpectedFree(symbols, isSimulation);
        
        return actions;
    }
    
    private Map<String, BigDecimal> detectDeposits(List<String> symbols, Boolean isSimulation) {
        Map<String, BigDecimal> depositByAsset = new HashMap<>();
        
        for (String symbol : symbols) {
            String quoteAsset = "USDT";
            
            ExpectedFree expected = expectedFreeRepository
                    .findByAssetAndIsSimulation(quoteAsset, isSimulation)
                    .orElse(null);
            
            BigDecimal currentFree = simAccountRepository
                    .findByAssetAndIsSimulation(quoteAsset, isSimulation)
                    .map(SimAccount::getFreeBalance)
                    .orElse(BigDecimal.ZERO);
            
            BigDecimal expectedValue = expected != null ? expected.getExpectedFree() : BigDecimal.ZERO;
            BigDecimal delta = currentFree.subtract(expectedValue);
            
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                depositByAsset.put(quoteAsset, delta);
            }
        }
        
        return depositByAsset;
    }
    
    private String selectSymbolForDeposit(String quoteAsset, List<String> symbols, Boolean isSimulation) {
        Map<String, Long> counts = new HashMap<>();
        for (String symbol : symbols) {
            counts.put(symbol, cycleInstanceRepository.countBySymbol(symbol, isSimulation));
        }
        
        long minCount = counts.values().stream().min(Long::compare).orElse(0L);
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getValue() == minCount) {
                candidates.add(entry.getKey());
            }
        }
        
        if (candidates.isEmpty()) return symbols.isEmpty() ? null : symbols.get(0);
        if (candidates.size() == 1) return candidates.get(0);
        
        Optional<RrState> rrOpt = rrStateRepository.findByQuoteAssetAndIsSimulation(quoteAsset, isSimulation);
        String lastSymbol = rrOpt.map(RrState::getLastSymbol).orElse("");
        
        List<String> sorted = new ArrayList<>(candidates);
        Collections.sort(sorted);
        
        if (!lastSymbol.isEmpty() && sorted.contains(lastSymbol)) {
            int idx = sorted.indexOf(lastSymbol);
            return sorted.get((idx + 1) % sorted.size());
        }
        
        return sorted.get(0);
    }
    
    private String tryOpenPosition(CycleInstance inst, BigDecimal price, BigDecimal spendableQuote, Boolean isSimulation) {
        BigDecimal reentryPrice = inst.getReentryPrice();
        if (reentryPrice.compareTo(BigDecimal.ZERO) > 0) {
            if (price.compareTo(reentryPrice) > 0) {
                return String.format("WAIT_REENTRY: %s price=%s > reentry=%s", 
                        inst.getSymbol(), price, reentryPrice);
            }
            inst.setReentryPrice(BigDecimal.ZERO);
        }
        
        BigDecimal quoteToSpend = inst.getQuoteAmount().min(spendableQuote);
        if (quoteToSpend.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        BigDecimal baseQty = quoteToSpend.divide(price, 16, RoundingMode.DOWN);
        BigDecimal cumQuote = baseQty.multiply(price).setScale(16, RoundingMode.DOWN);
        BigDecimal buyFeeBase = baseQty.multiply(FEE_RATE).setScale(16, RoundingMode.DOWN);
        BigDecimal netBaseQty = baseQty.subtract(buyFeeBase);
        BigDecimal totalCost = cumQuote;
        
        SimAccount quoteAccount = simAccountRepository
                .findByAssetAndIsSimulation("USDT", isSimulation)
                .orElse(null);
        if (quoteAccount != null) {
            quoteAccount.setFreeBalance(quoteAccount.getFreeBalance().subtract(totalCost));
            simAccountRepository.save(quoteAccount);
        }
        
        SimAccount baseAccount = simAccountRepository
                .findByAssetAndIsSimulation(inst.getSymbol().replace("USDT", ""), isSimulation)
                .orElseGet(() -> SimAccount.builder()
                        .asset(inst.getSymbol().replace("USDT", ""))
                        .freeBalance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .isSimulation(isSimulation)
                        .build());
        baseAccount.setFreeBalance(baseAccount.getFreeBalance().add(netBaseQty));
        simAccountRepository.save(baseAccount);
        
        inst.setIsOpen(true);
        inst.setBaseQty(netBaseQty);
        inst.setSpentQuote(totalCost);
        inst.setQuoteAmount(cumQuote);
        
        if (inst.getCycleStartPrice().compareTo(BigDecimal.ZERO) == 0) {
            inst.setCycleStartPrice(price);
            inst.setLastActionPrice(price);
            if (inst.getAnchorPrice().compareTo(BigDecimal.ZERO) == 0) {
                inst.setAnchorPrice(price);
            }
            
            CycleOpenRecord openRecord = CycleOpenRecord.builder()
                    .symbol(inst.getSymbol())
                    .instanceId(inst.getInstanceId())
                    .cycleId(inst.getCycleId())
                    .isSimulation(isSimulation)
                    .startPrice(price)
                    .quoteAmount(cumQuote)
                    .openedAtUtc(LocalDateTime.now())
                    .build();
            cycleOpenRecordRepository.save(openRecord);
        }
        
        cycleInstanceRepository.save(inst);
        
        recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), EVENT_BUY_OPEN,
                price, netBaseQty, cumQuote, "open_position fee=" + buyFeeBase, isSimulation);
        
        String msg = String.format("BUY_OPEN: %s instance#%d qty=%s at %s", 
                inst.getSymbol(), inst.getInstanceId(), netBaseQty, price);
        notificationService.notifyTradeEvent("开仓 (BUY_OPEN)", inst.getSymbol(), msg, isSimulation);
        return msg;
    }
    
    private String tryTakeProfitOrRebuy(CycleInstance inst, BigDecimal price, BigDecimal spendableQuote, Boolean isSimulation) {
        BigDecimal takeProfitPct = getTakeProfitPct(inst.getSymbol(), isSimulation);
        BigDecimal stopLossPct = getStopLossPct(inst.getSymbol(), isSimulation);
        BigDecimal anchorPrice = inst.getAnchorPrice();
        BigDecimal cycleStartPrice = inst.getCycleStartPrice();
        BigDecimal baseQty = inst.getBaseQty();
        
        if (baseQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        BigDecimal takeProfitPrice = cycleStartPrice.multiply(BigDecimal.ONE.add(takeProfitPct));
        BigDecimal stopLossPrice = cycleStartPrice.multiply(BigDecimal.ONE.subtract(stopLossPct));
        
        if (price.compareTo(takeProfitPrice) >= 0 && takeProfitPct.compareTo(BigDecimal.ZERO) > 0) {
            return executeTakeProfit(inst, price, baseQty, isSimulation);
        } else if (price.compareTo(stopLossPrice) <= 0 && stopLossPct.compareTo(BigDecimal.ZERO) > 0) {
            return executeStopLoss(inst, price, baseQty, isSimulation);
        }
        
        return null;
    }
    
    private String executeStopLoss(CycleInstance inst, BigDecimal price, BigDecimal baseQty, Boolean isSimulation) {
        BigDecimal cumQuote = baseQty.multiply(price).setScale(16, RoundingMode.DOWN);
        BigDecimal sellFee = cumQuote.multiply(TAKER_FEE_RATE).setScale(16, RoundingMode.DOWN);
        BigDecimal netQuote = cumQuote.subtract(sellFee);
        
        SimAccount baseAccount = simAccountRepository
                .findByAssetAndIsSimulation(inst.getSymbol().replace("USDT", ""), isSimulation)
                .orElse(null);
        if (baseAccount != null) {
            baseAccount.setFreeBalance(baseAccount.getFreeBalance().subtract(baseQty));
            simAccountRepository.save(baseAccount);
        }
        
        SimAccount quoteAccount = simAccountRepository
                .findByAssetAndIsSimulation("USDT", isSimulation)
                .orElseGet(() -> SimAccount.builder()
                        .asset("USDT")
                        .freeBalance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .isSimulation(isSimulation)
                        .build());
        quoteAccount.setFreeBalance(quoteAccount.getFreeBalance().add(netQuote));
        simAccountRepository.save(quoteAccount);
        
        BigDecimal profit = netQuote.subtract(inst.getSpentQuote());
        
        inst.setIsOpen(false);
        inst.setBaseQty(BigDecimal.ZERO);
        inst.setSpentQuote(BigDecimal.ZERO);
        inst.setQuoteAmount(netQuote);
        inst.setCycleStartPrice(BigDecimal.ZERO);
        inst.setLastActionPrice(price);
        inst.setReentryPrice(BigDecimal.ZERO); // Reset reentry on stop loss
        inst.setCycleId(inst.getCycleId() + 1);
        
        cycleInstanceRepository.save(inst);
        
        recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), "STOP_LOSS",
                price, baseQty, netQuote, "loss=" + profit, isSimulation);
        
        log.info("STOP_LOSS: {} instance#{} loss={} quote={}", 
                inst.getSymbol(), inst.getInstanceId(), profit, netQuote);
        
        String msg = String.format("STOP_LOSS: %s instance#%d loss=%s at %s",
                inst.getSymbol(), inst.getInstanceId(), profit, price);
        notificationService.notifyTradeEvent("止损 (STOP_LOSS)", inst.getSymbol(), msg, isSimulation);
        return msg;
    }
    
    private String executeTakeProfit(CycleInstance inst, BigDecimal price, BigDecimal baseQty, Boolean isSimulation) {
        BigDecimal cumQuote = baseQty.multiply(price).setScale(16, RoundingMode.DOWN);
        BigDecimal sellFee = cumQuote.multiply(TAKER_FEE_RATE).setScale(16, RoundingMode.DOWN);
        BigDecimal netQuote = cumQuote.subtract(sellFee);
        
        SimAccount baseAccount = simAccountRepository
                .findByAssetAndIsSimulation(inst.getSymbol().replace("USDT", ""), isSimulation)
                .orElse(null);
        if (baseAccount != null) {
            baseAccount.setFreeBalance(baseAccount.getFreeBalance().subtract(baseQty));
            simAccountRepository.save(baseAccount);
        }
        
        SimAccount quoteAccount = simAccountRepository
                .findByAssetAndIsSimulation("USDT", isSimulation)
                .orElseGet(() -> SimAccount.builder()
                        .asset("USDT")
                        .freeBalance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .isSimulation(isSimulation)
                        .build());
        quoteAccount.setFreeBalance(quoteAccount.getFreeBalance().add(netQuote));
        simAccountRepository.save(quoteAccount);
        
        BigDecimal profit = netQuote.subtract(inst.getSpentQuote());
        
        inst.setIsOpen(false);
        inst.setBaseQty(BigDecimal.ZERO);
        inst.setSpentQuote(BigDecimal.ZERO);
        inst.setQuoteAmount(netQuote);
        inst.setCycleStartPrice(BigDecimal.ZERO);
        inst.setLastActionPrice(price);
        inst.setReentryPrice(inst.getAnchorPrice());
        inst.setCycleId(inst.getCycleId() + 1);
        
        cycleInstanceRepository.save(inst);
        
        recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), EVENT_TAKE_PROFIT,
                price, baseQty, netQuote, "profit=" + profit, isSimulation);
        
        log.info("TAKE_PROFIT: {} instance#{} profit={} quote={}", 
                inst.getSymbol(), inst.getInstanceId(), profit, netQuote);
        
        String msg = String.format("TAKE_PROFIT: %s instance#%d profit=%s at %s",
                inst.getSymbol(), inst.getInstanceId(), profit, price);
        notificationService.notifyTradeEvent("止盈 (TAKE_PROFIT)", inst.getSymbol(), msg, isSimulation);
        return msg;
    }
    
    @Transactional
    public String triggerRebuy(Long instanceId, BigDecimal price, Boolean isSimulation) {
        Optional<CycleInstance> instOpt = cycleInstanceRepository.findById(instanceId);
        if (instOpt.isEmpty()) {
            return "Instance not found";
        }
        
        CycleInstance inst = instOpt.get();
        if (inst.getIsOpen()) {
            return "Position already open";
        }
        
        BigDecimal spendableQuote = getSpendableQuote(inst.getSymbol(), "USDT", isSimulation);
        BigDecimal quoteToSpend = inst.getQuoteAmount().min(spendableQuote);
        
        if (quoteToSpend.compareTo(BigDecimal.ZERO) <= 0) {
            return "No quote to spend";
        }
        
        BigDecimal baseQty = quoteToSpend.divide(price, 16, RoundingMode.DOWN);
        BigDecimal cumQuote = baseQty.multiply(price).setScale(16, RoundingMode.DOWN);
        BigDecimal buyFeeBase = baseQty.multiply(FEE_RATE).setScale(16, RoundingMode.DOWN);
        BigDecimal netBaseQty = baseQty.subtract(buyFeeBase);
        BigDecimal totalCost = cumQuote;
        
        SimAccount quoteAccount = simAccountRepository
                .findByAssetAndIsSimulation("USDT", isSimulation)
                .orElse(null);
        if (quoteAccount != null) {
            quoteAccount.setFreeBalance(quoteAccount.getFreeBalance().subtract(totalCost));
            simAccountRepository.save(quoteAccount);
        }
        
        SimAccount baseAccount = simAccountRepository
                .findByAssetAndIsSimulation(inst.getSymbol().replace("USDT", ""), isSimulation)
                .orElseGet(() -> SimAccount.builder()
                        .asset(inst.getSymbol().replace("USDT", ""))
                        .freeBalance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .isSimulation(isSimulation)
                        .build());
        baseAccount.setFreeBalance(baseAccount.getFreeBalance().add(netBaseQty));
        simAccountRepository.save(baseAccount);
        
        inst.setIsOpen(true);
        inst.setBaseQty(netBaseQty);
        inst.setSpentQuote(totalCost);
        inst.setQuoteAmount(cumQuote);
        inst.setCycleId(inst.getCycleId() + 1);
        inst.setCycleStartPrice(price);
        inst.setLastActionPrice(price);
        
        CycleOpenRecord openRecord = CycleOpenRecord.builder()
                .symbol(inst.getSymbol())
                .instanceId(inst.getInstanceId())
                .cycleId(inst.getCycleId())
                .isSimulation(isSimulation)
                .startPrice(price)
                .quoteAmount(cumQuote)
                .openedAtUtc(LocalDateTime.now())
                .build();
        cycleOpenRecordRepository.save(openRecord);
        
        cycleInstanceRepository.save(inst);
        
        recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), EVENT_REBUY_COMPOUND,
                price, netBaseQty, cumQuote, "rebuy_compound fee=" + buyFeeBase, isSimulation);
        
        log.info("REBUY_COMPOUND: {} instance#{} cycle={} qty={} at {}",
                inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), netBaseQty, price);
        
        String msg = String.format("REBUY_COMPOUND: %s instance#%d cycle=%d qty=%s at %s",
                inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), netBaseQty, price);
        notificationService.notifyTradeEvent("复利买入 (REBUY_COMPOUND)", inst.getSymbol(), msg, isSimulation);
        return msg;
    }
    
    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal getTakeProfitPct(String symbol, Boolean isSimulation) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("TAKE_PROFIT_PCT_" + symbol, isSimulation)
                .map(c -> parseBigDecimal(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("TAKE_PROFIT_PCT", isSimulation)
                        .map(c -> parseBigDecimal(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultTakeProfitPct));
    }
    
    private BigDecimal getStopLossPct(String symbol, Boolean isSimulation) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("STOP_LOSS_PCT_" + symbol, isSimulation)
                .map(c -> parseBigDecimal(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("STOP_LOSS_PCT", isSimulation)
                        .map(c -> parseBigDecimal(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultStopLossPct));
    }
    
    private BigDecimal getQuoteReserve(String symbol, Boolean isSimulation) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("QUOTE_RESERVE_" + symbol, isSimulation)
                .map(c -> parseBigDecimal(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("QUOTE_RESERVE", isSimulation)
                        .map(c -> parseBigDecimal(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultQuoteReserve));
    }
    
    private Integer getMaxOrdersPerTick(String symbol, Boolean isSimulation) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("MAX_ORDERS_PER_TICK_" + symbol, isSimulation)
                .map(c -> parseInteger(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("MAX_ORDERS_PER_TICK", isSimulation)
                        .map(c -> parseInteger(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultMaxOrdersPerTick));
    }
    
    private BigDecimal getSpendableQuote(String symbol, String quoteAsset, Boolean isSimulation) {
        BigDecimal free = simAccountRepository.findByAssetAndIsSimulation(quoteAsset, isSimulation)
                .map(SimAccount::getFreeBalance)
                .orElse(BigDecimal.ZERO);
        BigDecimal reserve = getQuoteReserve(symbol, isSimulation);
        return free.subtract(reserve).max(BigDecimal.ZERO);
    }
    
    private void updateExpectedFree(List<String> symbols, Boolean isSimulation) {
        for (String symbol : symbols) {
            BigDecimal free = simAccountRepository.findByAssetAndIsSimulation("USDT", isSimulation)
                    .map(SimAccount::getFreeBalance)
                    .orElse(BigDecimal.ZERO);
            
            ExpectedFree expected = expectedFreeRepository
                    .findByAssetAndIsSimulation("USDT", isSimulation)
                    .orElseGet(() -> ExpectedFree.builder()
                            .asset("USDT")
                            .expectedFree(BigDecimal.ZERO)
                            .isSimulation(isSimulation)
                            .build());
            expected.setExpectedFree(free);
            expectedFreeRepository.save(expected);
        }
    }
    
    private void recordEvent(String symbol, Integer instanceId, Integer cycleId, 
            String event, BigDecimal price, BigDecimal baseQty, BigDecimal quoteAmount, 
            String note, Boolean isSimulation) {
        InstanceEvent e = InstanceEvent.builder()
                .symbol(symbol)
                .instanceId(instanceId)
                .cycleId(cycleId)
                .isSimulation(isSimulation)
                .event(event)
                .price(price)
                .baseQty(baseQty)
                .quoteAmount(quoteAmount)
                .note(note)
                .build();
        instanceEventRepository.save(e);
    }
    
    @Transactional(readOnly = true)
    public List<CycleInstanceDto> getAllInstances(Boolean isSimulation) {
        return cycleInstanceRepository.findByIsSimulationOrderBySymbolAscInstanceIdAsc(isSimulation)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public List<CycleInstanceDto> getInstancesBySymbol(String symbol, Boolean isSimulation) {
        return cycleInstanceRepository
                .findBySymbolAndIsSimulationOrderByInstanceIdAsc(symbol, isSimulation)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    private CycleInstanceDto toDto(CycleInstance entity) {
        return CycleInstanceDto.builder()
                .id(entity.getId())
                .symbol(entity.getSymbol())
                .instanceId(entity.getInstanceId())
                .cycleId(entity.getCycleId())
                .isSimulation(entity.getIsSimulation())
                .isOpen(entity.getIsOpen())
                .anchorPrice(entity.getAnchorPrice())
                .reentryPrice(entity.getReentryPrice())
                .cycleStartPrice(entity.getCycleStartPrice())
                .lastActionPrice(entity.getLastActionPrice())
                .baseQty(entity.getBaseQty())
                .spentQuote(entity.getSpentQuote())
                .quoteAmount(entity.getQuoteAmount())
                .updatedAtUtc(entity.getUpdatedAtUtc())
                .createdAtUtc(entity.getCreatedAtUtc())
                .build();
    }
    
    @Scheduled(fixedRateString = "${simulation.auto-tick-interval-ms:30000}")
    public void autoExecuteTick() {
        log.debug("Auto tick scheduler triggered, enabled={}", autoTickEnabled);
        if (autoTickEnabled == null || !autoTickEnabled) {
            log.debug("Auto tick is disabled");
            return;
        }
        
        try {
            log.info("Auto tick starting for symbols: {}", DEFAULT_SYMBOLS);
            List<String> actions = executeTick(DEFAULT_SYMBOLS, true);
            if (!actions.isEmpty()) {
                log.info("Auto tick executed, actions: {}", actions);
            } else {
                log.debug("Auto tick completed with no actions");
            }
        } catch (Exception e) {
            log.error("Auto tick failed: {}", e.getMessage(), e);
        }
    }
}