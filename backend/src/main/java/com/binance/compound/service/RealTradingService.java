package com.binance.compound.service;

import com.binance.compound.entity.ApiAccount;
import com.binance.compound.entity.CycleInstance;
import com.binance.compound.entity.CycleOpenRecord;
import com.binance.compound.entity.InstanceEvent;
import com.binance.compound.repository.ApiAccountRepository;
import com.binance.compound.repository.CycleInstanceRepository;
import com.binance.compound.repository.CycleOpenRecordRepository;
import com.binance.compound.repository.InstanceEventRepository;
import com.binance.compound.repository.StrategyConfigRepository;
import com.binance.compound.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealTradingService {
    
    private final ApiAccountRepository apiAccountRepository;
    private final CycleInstanceRepository cycleInstanceRepository;
    private final CycleOpenRecordRepository cycleOpenRecordRepository;
    private final InstanceEventRepository instanceEventRepository;
    private final StrategyConfigRepository strategyConfigRepository;
    private final BinanceApiService binanceApiService;
    private final PriceService priceService;
    private final NotificationService notificationService;
    
    private static final String EVENT_BUY_OPEN = "BUY_OPEN";
    private static final String EVENT_TAKE_PROFIT = "TAKE_PROFIT";
    private static final Boolean IS_SIMULATION = false;
    
    @Value("${trading.default-take-profit-pct:0.03}")
    private BigDecimal defaultTakeProfitPct;
    
    @Value("${trading.default-stop-loss-pct:0.10}")
    private BigDecimal defaultStopLossPct;
    
    @Value("${trading.default-quote-reserve:10}")
    private BigDecimal defaultQuoteReserve;
    
    @Value("${trading.default-max-orders-per-tick:5}")
    private Integer defaultMaxOrdersPerTick;
    
    @Value("${trading.auto-tick-enabled:false}")
    private Boolean autoTickEnabled;
    
    @Value("${trading.auto-tick-interval-ms:30000}")
    private Long autoTickIntervalMs;

    /**
     * @param allowInitialOpen true：手动真实 Tick 时，对每个选中交易对按 customQuoteAmount 市价首买新实例（next instanceId，可重复，与模拟多实例一致）；
     *                         false：定时任务，绝不自动首买。
     */
    @Transactional
    public Map<String, Object> executeRealTick(List<String> symbols, BigDecimal customQuoteAmount, boolean allowInitialOpen) {
        Map<String, Object> result = new HashMap<>();
        List<String> actions = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        
        ApiAccount activeAccount = apiAccountRepository.findByIsActiveTrue().orElse(null);
        if (activeAccount == null) {
            result.put("success", false);
            result.put("errors", List.of("没有激活的API账户"));
            return result;
        }
        
        String apiKey = activeAccount.getApiKey();
        String apiSecret = EncryptionUtil.decrypt(activeAccount.getApiSecret());
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        if (binanceApiService.getStepSize("BTCUSDT") == 8 && binanceApiService.getPricePrecision("BTCUSDT") == 8) {
            binanceApiService.updateExchangeInfo(testnet, proxyUrl);
        }
        
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String symbol : symbols) {
            BigDecimal price = priceService.getPrice(symbol);
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                prices.put(symbol, price);
            }
        }
        
        if (prices.isEmpty()) {
            result.put("success", false);
            result.put("errors", List.of("无法获取市场价格"));
            return result;
        }
        
        Map<String, Object> balanceResult = binanceApiService.getAccountBalances(apiKey, apiSecret, testnet, proxyUrl);
        if (!Boolean.TRUE.equals(balanceResult.get("success"))) {
            result.put("success", false);
            result.put("errors", balanceResult.get("errors") != null ? 
                    (List<String>) balanceResult.get("errors") : List.of("无法获取账户余额"));
            return result;
        }
        
        BigDecimal usdtBalance = BigDecimal.ZERO;
        Map<String, Object> accountData = (Map<String, Object>) balanceResult.get("account");
        if (accountData != null && accountData.containsKey("balances")) {
            List<Map<String, Object>> balances = (List<Map<String, Object>>) accountData.get("balances");
            for (Map<String, Object> bal : balances) {
                if ("USDT".equals(bal.get("asset"))) {
                    String freeStr = (String) bal.get("free");
                    if (freeStr != null && !freeStr.isEmpty()) {
                        usdtBalance = new BigDecimal(freeStr);
                    }
                }
            }
        }

        int remainingOrders = getMaxOrdersPerTick("GLOBAL");
        if (allowInitialOpen && customQuoteAmount != null && customQuoteAmount.compareTo(BigDecimal.ZERO) > 0) {
            for (String symbol : symbols) {
                if (remainingOrders <= 0) {
                    break;
                }
                if (!prices.containsKey(symbol)) {
                    continue;
                }
                Map<String, Object> openRes = manualOpenPosition(symbol, customQuoteAmount);
                if (Boolean.TRUE.equals(openRes.get("success"))) {
                    actions.add((String) openRes.get("message"));
                    remainingOrders--;
                    balanceResult = binanceApiService.getAccountBalances(apiKey, apiSecret, testnet, proxyUrl);
                    if (!Boolean.TRUE.equals(balanceResult.get("success"))) {
                        Map<String, Object> errMap = new HashMap<>();
                        errMap.put("symbol", symbol);
                        errMap.put("error", balanceResult.get("errors") != null
                                ? balanceResult.get("errors") : List.of("首买后刷新余额失败"));
                        errors.add(errMap);
                        break;
                    }
                    accountData = (Map<String, Object>) balanceResult.get("account");
                    usdtBalance = BigDecimal.ZERO;
                    if (accountData != null && accountData.containsKey("balances")) {
                        List<Map<String, Object>> bals = (List<Map<String, Object>>) accountData.get("balances");
                        for (Map<String, Object> bal : bals) {
                            if ("USDT".equals(bal.get("asset"))) {
                                String freeStr = (String) bal.get("free");
                                if (freeStr != null && !freeStr.isEmpty()) {
                                    usdtBalance = new BigDecimal(freeStr);
                                }
                            }
                        }
                    }
                } else {
                    Map<String, Object> errMap = new HashMap<>();
                    errMap.put("symbol", symbol);
                    errMap.put("error", openRes.get("errors") != null
                            ? openRes.get("errors") : List.of("手动Tick首买失败"));
                    errors.add(errMap);
                    log.warn("REAL TICK initial open failed for {}: {}", symbol, openRes.get("errors"));
                }
            }
        }
        
        List<CycleInstance> allInstances = cycleInstanceRepository
                .findBySymbolInAndIsSimulationAndIsOpenTrue(symbols, IS_SIMULATION);
        
        log.info("REAL TICK allInstances count: {}", allInstances.size());
        for (CycleInstance inst : allInstances) {
            log.info("REAL TICK instance: symbol={}, id={}, isOpen={}", inst.getSymbol(), inst.getInstanceId(), inst.getIsOpen());
        }
        
        // Also get closed instances that are waiting for reentry
        List<CycleInstance> closedInstances = cycleInstanceRepository
                .findBySymbolInAndIsSimulationAndIsOpenFalse(symbols, IS_SIMULATION);
        
        for (CycleInstance inst : closedInstances) {
            if (inst.getReentryPrice() != null && inst.getReentryPrice().compareTo(BigDecimal.ZERO) > 0) {
                allInstances.add(inst);
            }
        }
        
        int takeProfitCount = 0;
        int rebuyCount = 0;
        
        for (CycleInstance inst : allInstances) {
            BigDecimal price = prices.get(inst.getSymbol());
            if (price == null) continue;
            
            if (inst.getIsOpen()) {
                BigDecimal takeProfitPct = getTakeProfitPct(inst.getSymbol());
                BigDecimal stopLossPct = getStopLossPct(inst.getSymbol());
                BigDecimal cycleStartPrice = inst.getCycleStartPrice();
                BigDecimal takeProfitPrice = cycleStartPrice.multiply(BigDecimal.ONE.add(takeProfitPct));
                BigDecimal stopLossPrice = cycleStartPrice.multiply(BigDecimal.ONE.subtract(stopLossPct));
                
                if (price.compareTo(takeProfitPrice) >= 0 && takeProfitPct.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("REAL TICK: price {} >= takeProfitPrice {} for {}", price, takeProfitPrice, inst.getSymbol());
                    Map<String, Object> closeResult = closePositionInternal(inst, activeAccount, accountData, EVENT_TAKE_PROFIT);
                    if (Boolean.TRUE.equals(closeResult.get("success"))) {
                        actions.add((String) closeResult.get("message"));
                        takeProfitCount++;
                        log.info("REAL TAKE_PROFIT: {} profit={}", inst.getSymbol(), closeResult.get("profit"));
                    } else {
                        log.warn("REAL TAKE_PROFIT FAILED for {}: {}", inst.getSymbol(), closeResult.get("errors"));
                        Map<String, Object> errMap = new HashMap<>();
                        errMap.put("symbol", inst.getSymbol());
                        errMap.put("error", closeResult.get("errors"));
                        errors.add(errMap);
                    }
                } else if (price.compareTo(stopLossPrice) <= 0 && stopLossPct.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, Object> closeResult = closePositionInternal(inst, activeAccount, accountData, "STOP_LOSS");
                    if (Boolean.TRUE.equals(closeResult.get("success"))) {
                        actions.add((String) closeResult.get("message"));
                        takeProfitCount++;
                        log.info("REAL STOP_LOSS: {} profit={}", inst.getSymbol(), closeResult.get("profit"));
                    }
                }
            } else {
                // Closed instance waiting for reentry
                if (price.compareTo(inst.getReentryPrice()) <= 0) {
                    Map<String, Object> rebuyResult = executeRebuyCompound(inst, price, activeAccount, usdtBalance);
                    if (Boolean.TRUE.equals(rebuyResult.get("success"))) {
                        actions.add((String) rebuyResult.get("message"));
                        rebuyCount++;
                        log.info("REAL REBUY_COMPOUND: {} at {}", inst.getSymbol(), price);
                        // Deduct spent quote from local balance to prevent overspending in this tick
                        BigDecimal spent = new BigDecimal((String) rebuyResult.get("spentQuote"));
                        usdtBalance = usdtBalance.subtract(spent);
                    }
                }
            }
        }
        
        result.put("success", true);
        result.put("actions", actions);
        result.put("errors", errors);
        result.put("usdtBalance", usdtBalance.toPlainString());

        StringBuilder tickMsg = new StringBuilder("完成");
        if (actions.isEmpty()) {
            tickMsg.append("，本轮无交易动作");
        } else {
            tickMsg.append("，动作数: ").append(actions.size());
        }
        if (!errors.isEmpty()) {
            tickMsg.append("（含 ").append(errors.size()).append(" 条告警）");
        }
        result.put("message", tickMsg.toString());

        return result;
    }
    
    @Transactional
    public Map<String, Object> manualOpenPosition(String symbol, BigDecimal quoteAmount) {
        Map<String, Object> result = new HashMap<>();
        
        ApiAccount activeAccount = apiAccountRepository.findByIsActiveTrue().orElse(null);
        if (activeAccount == null) {
            result.put("success", false);
            result.put("errors", List.of("没有激活的API账户"));
            return result;
        }
        
        if (quoteAmount == null || quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", false);
            result.put("errors", List.of("投入金额必须大于0"));
            return result;
        }
        
        String apiKey = activeAccount.getApiKey();
        String apiSecret = EncryptionUtil.decrypt(activeAccount.getApiSecret());
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        BigDecimal price = priceService.getPrice(symbol);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", false);
            result.put("errors", List.of("无法获取市场价格"));
            return result;
        }
        
        Map<String, Object> balanceResult = binanceApiService.getAccountBalances(apiKey, apiSecret, testnet, proxyUrl);
        if (!Boolean.TRUE.equals(balanceResult.get("success"))) {
            result.put("success", false);
            result.put("errors", balanceResult.get("errors") != null ? 
                    (List<String>) balanceResult.get("errors") : List.of("无法获取账户余额"));
            return result;
        }
        
        BigDecimal usdtBalance = BigDecimal.ZERO;
        Map<String, Object> accountData = (Map<String, Object>) balanceResult.get("account");
        if (accountData != null && accountData.containsKey("balances")) {
            List<Map<String, Object>> balances = (List<Map<String, Object>>) accountData.get("balances");
            for (Map<String, Object> bal : balances) {
                if ("USDT".equals(bal.get("asset"))) {
                    String freeStr = (String) bal.get("free");
                    if (freeStr != null && !freeStr.isEmpty()) {
                        usdtBalance = new BigDecimal(freeStr);
                    }
                }
            }
        }
        
        if (usdtBalance.compareTo(quoteAmount) < 0) {
            result.put("success", false);
            result.put("errors", List.of("USDT余额不足, 可用: " + usdtBalance));
            return result;
        }
        
        Integer nextInstanceId = cycleInstanceRepository.findNextInstanceId(symbol, IS_SIMULATION);
        
        Map<String, Object> orderResult = binanceApiService.placeMarketBuyOrder(
                symbol, quoteAmount.toPlainString(), apiKey, apiSecret, testnet, proxyUrl);
        
        if (Boolean.TRUE.equals(orderResult.get("success"))) {
            String executedQty = (String) orderResult.get("executedQty");
            String cummulativeQuoteQty = (String) orderResult.get("cummulativeQuoteQty");
            
            CycleInstance inst = CycleInstance.builder()
                    .symbol(symbol)
                    .instanceId(nextInstanceId)
                    .cycleId(0)
                    .isSimulation(IS_SIMULATION)
                    .isOpen(true)
                    .anchorPrice(price)
                    .reentryPrice(BigDecimal.ZERO)
                    .cycleStartPrice(price)
                    .lastActionPrice(price)
                    .baseQty(new BigDecimal(executedQty))
                    .spentQuote(new BigDecimal(cummulativeQuoteQty))
                    .quoteAmount(new BigDecimal(cummulativeQuoteQty))
                    .apiAccountId(activeAccount.getId())
                    .build();
            
            cycleInstanceRepository.save(inst);
            
            CycleOpenRecord openRecord = CycleOpenRecord.builder()
                    .symbol(symbol)
                    .instanceId(nextInstanceId)
                    .cycleId(0)
                    .isSimulation(IS_SIMULATION)
                    .startPrice(price)
                    .quoteAmount(new BigDecimal(cummulativeQuoteQty))
                    .openedAtUtc(LocalDateTime.now())
                    .apiAccountId(activeAccount.getId())
                    .build();
            cycleOpenRecordRepository.save(openRecord);
            
            recordEvent(symbol, nextInstanceId, 0, EVENT_BUY_OPEN, price, 
                    new BigDecimal(executedQty), new BigDecimal(cummulativeQuoteQty),
                    "real_order_id=" + orderResult.get("orderId"), IS_SIMULATION);
            
            String msg = String.format("手动开仓成功: %s instance#%d qty=%s at %s orderId=%s",
                    symbol, nextInstanceId, executedQty, price, orderResult.get("orderId"));
            notificationService.notifyTradeEvent("手动开仓 (MANUAL_OPEN)", symbol, msg, IS_SIMULATION);
            
            log.info("REAL MANUAL_OPEN: {} instance#{} qty={} at {} orderId={}", 
                    symbol, nextInstanceId, executedQty, price, orderResult.get("orderId"));
                    
            result.put("success", true);
            result.put("message", msg);
        } else {
            List<String> orderErrors = orderResult.get("errors") != null ? 
                    (List<String>) orderResult.get("errors") : List.of("下单失败");
            result.put("success", false);
            result.put("errors", orderErrors);
            log.warn("REAL MANUAL_OPEN_FAILED: {} errors={}", symbol, orderErrors);
        }
        
        return result;
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

    private BigDecimal getTakeProfitPct(String symbol) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("TAKE_PROFIT_PCT_" + symbol, IS_SIMULATION)
                .map(c -> parseBigDecimal(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("TAKE_PROFIT_PCT", IS_SIMULATION)
                        .map(c -> parseBigDecimal(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultTakeProfitPct));
    }
    
    private BigDecimal getStopLossPct(String symbol) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("STOP_LOSS_PCT_" + symbol, IS_SIMULATION)
                .map(c -> parseBigDecimal(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("STOP_LOSS_PCT", IS_SIMULATION)
                        .map(c -> parseBigDecimal(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultStopLossPct));
    }
    
    private BigDecimal getQuoteReserve(String symbol) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("QUOTE_RESERVE_" + symbol, IS_SIMULATION)
                .map(c -> parseBigDecimal(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("QUOTE_RESERVE", IS_SIMULATION)
                        .map(c -> parseBigDecimal(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultQuoteReserve));
    }
    
    private Integer getMaxOrdersPerTick(String symbol) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("MAX_ORDERS_PER_TICK_" + symbol, IS_SIMULATION)
                .map(c -> parseInteger(c.getConfigValue()))
                .filter(Objects::nonNull)
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("MAX_ORDERS_PER_TICK", IS_SIMULATION)
                        .map(c -> parseInteger(c.getConfigValue()))
                        .filter(Objects::nonNull)
                        .orElse(defaultMaxOrdersPerTick));
    }
    
    @Transactional
    public Map<String, Object> closePosition(Long instanceId) {
        Map<String, Object> result = new HashMap<>();
        
        ApiAccount activeAccount = apiAccountRepository.findByIsActiveTrue().orElse(null);
        if (activeAccount == null) {
            result.put("success", false);
            result.put("errors", List.of("没有激活的API账户"));
            return result;
        }
        
        Optional<CycleInstance> instOpt = cycleInstanceRepository.findById(instanceId);
        if (instOpt.isEmpty()) {
            result.put("success", false);
            result.put("errors", List.of("仓位不存在"));
            return result;
        }
        
        CycleInstance inst = instOpt.get();
        if (!inst.getIsOpen()) {
            result.put("success", false);
            result.put("errors", List.of("仓位已经关闭"));
            return result;
        }
        
        String apiKey = activeAccount.getApiKey();
        String apiSecret = EncryptionUtil.decrypt(activeAccount.getApiSecret());
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        Map<String, Object> balanceResult = binanceApiService.getAccountBalances(apiKey, apiSecret, testnet, proxyUrl);
        if (!Boolean.TRUE.equals(balanceResult.get("success"))) {
            result.put("success", false);
            result.put("errors", balanceResult.get("errors") != null ? 
                    (List<String>) balanceResult.get("errors") : List.of("无法获取账户余额"));
            return result;
        }
        
        Map<String, Object> accountData = (Map<String, Object>) balanceResult.get("account");
        return closePositionInternal(inst, activeAccount, accountData, EVENT_TAKE_PROFIT);
    }
    
    private Map<String, Object> closePositionInternal(CycleInstance inst, ApiAccount activeAccount, Map<String, Object> accountData, String eventType) {
        Map<String, Object> result = new HashMap<>();
        
        String apiKey = activeAccount.getApiKey();
        String apiSecret = EncryptionUtil.decrypt(activeAccount.getApiSecret());
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        String baseAsset = inst.getSymbol().replace("USDT", "");
        
        BigDecimal baseBalance = BigDecimal.ZERO;
        if (accountData != null && accountData.containsKey("balances")) {
            List<Map<String, Object>> balances = (List<Map<String, Object>>) accountData.get("balances");
            for (Map<String, Object> bal : balances) {
                if (baseAsset.equals(bal.get("asset"))) {
                    String freeStr = (String) bal.get("free");
                    if (freeStr != null && !freeStr.isEmpty()) {
                        baseBalance = new BigDecimal(freeStr);
                    }
                }
            }
        }
        
        if (inst.getBaseQty().compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", false);
            result.put("errors", List.of(inst.getSymbol() + " 持仓为0"));
            return result;
        }

        String quantityStr = formatQuantity(inst.getBaseQty().toPlainString(), getStepSize(inst.getSymbol()));
        log.info("REAL TAKE_PROFIT: sending quantity {} for {}", quantityStr, inst.getSymbol());
        
        Map<String, Object> orderResult = binanceApiService.placeMarketSellOrder(
                inst.getSymbol(), quantityStr, apiKey, apiSecret, testnet, proxyUrl);
        
        if (Boolean.TRUE.equals(orderResult.get("success"))) {
            String executedQty = (String) orderResult.get("executedQty");
            String cummulativeQuoteQty = (String) orderResult.get("cummulativeQuoteQty");
            BigDecimal netQuote = new BigDecimal(cummulativeQuoteQty);
            BigDecimal buyFee = inst.getSpentQuote().multiply(new BigDecimal("0.001"));
            BigDecimal profit = netQuote.subtract(inst.getSpentQuote()).subtract(buyFee);
            
            inst.setIsOpen(false);
            inst.setBaseQty(BigDecimal.ZERO);
            inst.setLastActionPrice(new BigDecimal((String) orderResult.get("price")));
            if ("STOP_LOSS".equals(eventType)) {
                inst.setReentryPrice(BigDecimal.ZERO);
            } else {
                inst.setReentryPrice(inst.getAnchorPrice());
            }
            cycleInstanceRepository.save(inst);
            
            recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), eventType,
                    new BigDecimal((String) orderResult.get("price")),
                    new BigDecimal(executedQty), netQuote,
                    "profit=" + profit + " buyFee=" + buyFee + " real_order_id=" + orderResult.get("orderId"), false);
            
            result.put("success", true);
            result.put("profit", profit.toPlainString());
            result.put("orderId", orderResult.get("orderId"));
            String msg = String.format("平仓成功(%s): %s 盈利=%s (扣买入手续费%s)", eventType, inst.getSymbol(), profit, buyFee);
            result.put("message", msg);
            
            notificationService.notifyTradeEvent("TAKE_PROFIT".equals(eventType) ? "止盈 (TAKE_PROFIT)" : "止损 (STOP_LOSS)", inst.getSymbol(), msg, IS_SIMULATION);
            
            log.info("REAL {}: {} profit={} buyFee={} orderId={}", eventType, inst.getSymbol(), profit, buyFee, orderResult.get("orderId"));
        } else {
            result.put("success", false);
            result.put("errors", orderResult.get("errors") != null ? 
                    (List<String>) orderResult.get("errors") : List.of("平仓失败"));
        }
        
        return result;
    }
    
    private Map<String, Object> executeRebuyCompound(CycleInstance inst, BigDecimal price, ApiAccount activeAccount, BigDecimal currentUsdtBalance) {
        Map<String, Object> result = new HashMap<>();
        
        BigDecimal reserve = getQuoteReserve(inst.getSymbol());
        BigDecimal spendableQuote = currentUsdtBalance.subtract(reserve).max(BigDecimal.ZERO);
        
        BigDecimal quoteToSpend = inst.getQuoteAmount().min(spendableQuote);
        
        if (quoteToSpend.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", false);
            result.put("errors", List.of("可用USDT不足以复利买入"));
            return result;
        }
        
        String apiKey = activeAccount.getApiKey();
        String apiSecret = EncryptionUtil.decrypt(activeAccount.getApiSecret());
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        Map<String, Object> orderResult = binanceApiService.placeMarketBuyOrder(
                inst.getSymbol(), quoteToSpend.toPlainString(), apiKey, apiSecret, testnet, proxyUrl);
                
        if (Boolean.TRUE.equals(orderResult.get("success"))) {
            String executedQty = (String) orderResult.get("executedQty");
            String cummulativeQuoteQty = (String) orderResult.get("cummulativeQuoteQty");
            BigDecimal actualSpent = new BigDecimal(cummulativeQuoteQty);
            
            inst.setIsOpen(true);
            inst.setBaseQty(new BigDecimal(executedQty));
            inst.setSpentQuote(actualSpent);
            inst.setQuoteAmount(actualSpent);
            inst.setCycleId(inst.getCycleId() + 1);
            inst.setCycleStartPrice(price);
            inst.setLastActionPrice(price);
            inst.setReentryPrice(BigDecimal.ZERO);
            
            cycleInstanceRepository.save(inst);
            
            CycleOpenRecord openRecord = CycleOpenRecord.builder()
                    .symbol(inst.getSymbol())
                    .instanceId(inst.getInstanceId())
                    .cycleId(inst.getCycleId())
                    .isSimulation(IS_SIMULATION)
                    .startPrice(price)
                    .quoteAmount(actualSpent)
                    .openedAtUtc(LocalDateTime.now())
                    .apiAccountId(activeAccount.getId())
                    .build();
            cycleOpenRecordRepository.save(openRecord);
            
            recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), "REBUY_COMPOUND",
                    price, new BigDecimal(executedQty), actualSpent,
                    "real_order_id=" + orderResult.get("orderId"), IS_SIMULATION);
            
            result.put("success", true);
            result.put("spentQuote", cummulativeQuoteQty);
            String msg = String.format("REBUY_COMPOUND: %s instance#%d cycle=%d qty=%s at %s",
                    inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), executedQty, price);
            result.put("message", msg);
            
            notificationService.notifyTradeEvent("复利买入 (REBUY_COMPOUND)", inst.getSymbol(), msg, IS_SIMULATION);
        } else {
            result.put("success", false);
            result.put("errors", orderResult.get("errors") != null ? 
                    (List<String>) orderResult.get("errors") : List.of("复利买入下单失败"));
        }
        
        return result;
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
    
    private String formatQuantity(String qty, int stepSize) {
        try {
            BigDecimal bd = new BigDecimal(qty);
            return bd.setScale(stepSize, RoundingMode.DOWN).toPlainString();
        } catch (Exception e) {
            return qty;
        }
    }
    
    private int getStepSize(String symbol) {
        return binanceApiService.getStepSize(symbol);
    }

    private static final List<String> DEFAULT_SYMBOLS = Arrays.asList(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "DOGEUSDT", "SOLUSDT");

    @org.springframework.scheduling.annotation.Scheduled(fixedRateString = "${trading.auto-tick-interval-ms:30000}")
    public void autoExecuteTick() {
        log.debug("Real auto tick scheduler triggered, enabled={}", autoTickEnabled);
        if (autoTickEnabled == null || !autoTickEnabled) {
            log.debug("Real auto tick is disabled");
            return;
        }
        
        try {
            log.info("Real auto tick starting for symbols: {}", DEFAULT_SYMBOLS);
            Map<String, Object> result = executeRealTick(DEFAULT_SYMBOLS, null, false);
            List<String> actions = (List<String>) result.get("actions");
            if (actions != null && !actions.isEmpty()) {
                log.info("Real auto tick executed, actions: {}", actions);
            } else {
                log.debug("Real auto tick completed with no actions");
            }
        } catch (Exception e) {
            log.error("Real auto tick failed: {}", e.getMessage(), e);
        }
    }
}