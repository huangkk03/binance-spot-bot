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
    
    private static final String EVENT_BUY_OPEN = "BUY_OPEN";
    private static final String EVENT_TAKE_PROFIT = "TAKE_PROFIT";
    private static final Boolean IS_SIMULATION = false;
    
    @Value("${trading.default-take-profit-pct:0.03}")
    private BigDecimal defaultTakeProfitPct;
    
    @Value("${trading.default-quote-reserve:10}")
    private BigDecimal defaultQuoteReserve;
    
    @Value("${trading.default-max-orders-per-tick:5}")
    private Integer defaultMaxOrdersPerTick;
    
    @Transactional
    public Map<String, Object> executeRealTick(List<String> symbols, BigDecimal customQuoteAmount) {
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
        String apiSecret = activeAccount.getApiSecret();
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String symbol : symbols) {
            Map<String, Object> priceResult = binanceApiService.getSpotPrice(symbol, testnet, proxyUrl);
            if (Boolean.TRUE.equals(priceResult.get("success"))) {
                String priceStr = (String) priceResult.get("price");
                if (priceStr != null && !priceStr.isEmpty()) {
                    prices.put(symbol, new BigDecimal(priceStr));
                }
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
        
        List<CycleInstance> openInstances = cycleInstanceRepository
                .findBySymbolInAndIsSimulationAndIsOpenTrue(symbols, IS_SIMULATION);
        
        int takeProfitCount = 0;
        for (CycleInstance inst : openInstances) {
            BigDecimal price = prices.get(inst.getSymbol());
            if (price == null) continue;
            
            BigDecimal takeProfitPct = getTakeProfitPct();
            BigDecimal cycleStartPrice = inst.getCycleStartPrice();
            BigDecimal takeProfitPrice = cycleStartPrice.multiply(BigDecimal.ONE.add(takeProfitPct));
            
            if (price.compareTo(takeProfitPrice) >= 0) {
                Map<String, Object> closeResult = closePosition(inst.getId());
                if (Boolean.TRUE.equals(closeResult.get("success"))) {
                    actions.add((String) closeResult.get("message"));
                    takeProfitCount++;
                    log.info("REAL TAKE_PROFIT: {} profit={}", inst.getSymbol(), closeResult.get("profit"));
                }
            }
        }
        
        BigDecimal spendableQuote;
        if (customQuoteAmount != null && customQuoteAmount.compareTo(BigDecimal.ZERO) > 0) {
            spendableQuote = customQuoteAmount.min(usdtBalance);
        } else {
            BigDecimal reserve = getQuoteReserve();
            spendableQuote = usdtBalance.subtract(reserve).max(BigDecimal.ZERO);
        }
        
        if (spendableQuote.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", true);
            result.put("actions", actions);
            result.put("errors", errors);
            result.put("usdtBalance", usdtBalance.toPlainString());
            result.put("spendableQuote", "0");
            result.put("ordersPlaced", 0);
            result.put("message", "USDT余额不足, 可用: " + usdtBalance);
            return result;
        }
        
        int maxOrders = getMaxOrdersPerTick();
        int ordersPlaced = takeProfitCount;
        
        for (String symbol : symbols) {
            if (ordersPlaced >= maxOrders) break;
            
            BigDecimal price = prices.get(symbol);
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            Integer nextInstanceId = cycleInstanceRepository.findNextInstanceId(symbol, IS_SIMULATION);
            
            BigDecimal quoteToSpend = spendableQuote.divide(BigDecimal.valueOf(symbols.size()), 8, RoundingMode.DOWN);
            if (quoteToSpend.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            Map<String, Object> orderResult = binanceApiService.placeMarketBuyOrder(
                    symbol, quoteToSpend.toPlainString(), apiKey, apiSecret, testnet, proxyUrl);
            
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
                
                actions.add(String.format("BUY_OPEN: %s instance#%d qty=%s at %s orderId=%s",
                        symbol, nextInstanceId, executedQty, price, orderResult.get("orderId")));
                ordersPlaced++;
                
                log.info("REAL BUY_OPEN: {} instance#{} qty={} at {} orderId={}", 
                        symbol, nextInstanceId, executedQty, price, orderResult.get("orderId"));
            } else {
                List<String> orderErrors = orderResult.get("errors") != null ? 
                        (List<String>) orderResult.get("errors") : List.of("下单失败");
                errors.add(Map.of("symbol", symbol, "errors", orderErrors));
                log.warn("REAL BUY_FAILED: {} errors={}", symbol, orderErrors);
            }
        }
        
        result.put("success", true);
        result.put("actions", actions);
        result.put("errors", errors);
        result.put("usdtBalance", usdtBalance.toPlainString());
        result.put("spendableQuote", spendableQuote.toPlainString());
        result.put("ordersPlaced", ordersPlaced - takeProfitCount);
        
        return result;
    }
    
    private BigDecimal getTakeProfitPct() {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("TAKE_PROFIT_PCT", IS_SIMULATION)
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(defaultTakeProfitPct);
    }
    
    private BigDecimal getQuoteReserve() {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("QUOTE_RESERVE", IS_SIMULATION)
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElse(defaultQuoteReserve);
    }
    
    private Integer getMaxOrdersPerTick() {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("MAX_ORDERS_PER_TICK", IS_SIMULATION)
                .map(c -> Integer.parseInt(c.getConfigValue()))
                .orElse(defaultMaxOrdersPerTick);
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
        String apiSecret = activeAccount.getApiSecret();
        boolean testnet = activeAccount.getTestnet();
        String proxyUrl = activeAccount.getUseProxy() ? activeAccount.getProxyUrl() : "";
        
        String baseAsset = inst.getSymbol().replace("USDT", "");
        Map<String, Object> balanceResult = binanceApiService.getAccountBalances(apiKey, apiSecret, testnet, proxyUrl);
        
        if (!Boolean.TRUE.equals(balanceResult.get("success"))) {
            result.put("success", false);
            result.put("errors", balanceResult.get("errors") != null ? 
                    (List<String>) balanceResult.get("errors") : List.of("无法获取账户余额"));
            return result;
        }
        
        BigDecimal baseBalance = BigDecimal.ZERO;
        Map<String, Object> accountData = (Map<String, Object>) balanceResult.get("account");
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
        
        if (baseBalance.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", false);
            result.put("errors", List.of(baseAsset + " 余额为0"));
            return result;
        }
        
        String quantityStr = formatQuantity(baseBalance.toPlainString(), getStepSize(inst.getSymbol()));
        
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
            cycleInstanceRepository.save(inst);
            
            recordEvent(inst.getSymbol(), inst.getInstanceId(), inst.getCycleId(), EVENT_TAKE_PROFIT,
                    new BigDecimal((String) orderResult.get("price")),
                    new BigDecimal(executedQty), netQuote,
                    "profit=" + profit + " buyFee=" + buyFee + " real_order_id=" + orderResult.get("orderId"), false);
            
            result.put("success", true);
            result.put("profit", profit.toPlainString());
            result.put("orderId", orderResult.get("orderId"));
            result.put("message", String.format("平仓成功: %s 盈利=%s (扣买入手续费%s)", inst.getSymbol(), profit, buyFee));
            
            log.info("REAL TAKE_PROFIT: {} profit={} buyFee={} orderId={}", inst.getSymbol(), profit, buyFee, orderResult.get("orderId"));
        } else {
            result.put("success", false);
            result.put("errors", orderResult.get("errors") != null ? 
                    (List<String>) orderResult.get("errors") : List.of("平仓失败"));
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
            int scale = Math.max(0, 8 - stepSize + 1);
            return bd.setScale(scale, RoundingMode.DOWN).toPlainString();
        } catch (Exception e) {
            return qty;
        }
    }
    
    private int getStepSize(String symbol) {
        return 8;
    }
}