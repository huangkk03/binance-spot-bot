package com.binance.compound.service;

import com.binance.compound.entity.PriceAlert;
import com.binance.compound.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TDScannerService {
    
    private final HttpClient httpClient;
    private final PriceAlertRepository priceAlertRepository;
    
    private static final String BINANCE_KLINE_URL = "https://api.binance.com/api/v3/klines";
    
    private static final String[] SYMBOLS = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "DOGEUSDT", "SOLUSDT"};
    private static final String[] INTERVALS = {"1h", "4h"};
    
    @Scheduled(fixedRate = 60000)
    public void scanTDIndicators() {
        for (String symbol : SYMBOLS) {
            for (String interval : INTERVALS) {
                try {
                    scanSymbolInterval(symbol, interval);
                } catch (Exception e) {
                    log.error("Error scanning TD for {} {}: {}", symbol, interval, e.getMessage());
                }
            }
        }
    }
    
    @Transactional
    private void scanSymbolInterval(String symbol, String interval) {
        List<KlineBar> klines = fetchKlines(symbol, interval, 100);
        if (klines == null || klines.size() < 14) {
            return;
        }
        
        TDResult tdResult = calculateTDCount(klines);
        
        PriceAlert existingBuy = priceAlertRepository
                .findBySymbolAndIntervalAndAlertType(symbol, interval, "TD_BUY").orElse(null);
        PriceAlert existingSell = priceAlertRepository
                .findBySymbolAndIntervalAndAlertType(symbol, interval, "TD_SELL").orElse(null);
        
        BigDecimal currentPrice = klines.get(klines.size() - 1).close;
        
        // sellSetupCount means price is rising -> leading to a Sell Setup
        // buySetupCount means price is dropping -> leading to a Buy Setup
        if (tdResult.sellSetupCount >= 9) {
            handleAlert(symbol, interval, "TD_SELL", tdResult.sellSetupCount, currentPrice, tdResult.sellSetupCount, existingSell, tdResult.isPerfectSell);
        }
        
        if (tdResult.buySetupCount >= 9) {
            handleAlert(symbol, interval, "TD_BUY", tdResult.buySetupCount, currentPrice, tdResult.buySetupCount, existingBuy, tdResult.isPerfectBuy);
        }
        
        if (tdResult.buySetupCount < 9 && existingBuy != null) {
            existingBuy.setTriggered(false);
            priceAlertRepository.save(existingBuy);
        }
        
        if (tdResult.sellSetupCount < 9 && existingSell != null) {
            existingSell.setTriggered(false);
            priceAlertRepository.save(existingSell);
        }
    }
    
    private void handleAlert(String symbol, String interval, String alertType, int tdCount, 
            BigDecimal currentPrice, int count, PriceAlert existing, boolean isPerfect) {
        
        boolean isNew = existing == null;
        PriceAlert alert;
        
        if (isNew) {
            alert = PriceAlert.builder()
                    .symbol(symbol)
                    .interval(interval)
                    .alertType(alertType)
                    .tdCount(tdCount)
                    .currentPrice(currentPrice)
                    .triggerPrice(currentPrice)
                    .triggered(false)
                    .message(buildMessage(symbol, interval, alertType, tdCount, currentPrice, isPerfect))
                    .build();
            priceAlertRepository.save(alert);
        } else {
            alert = existing;
            alert.setTdCount(tdCount);
            alert.setCurrentPrice(currentPrice);
            alert.setMessage(buildMessage(symbol, interval, alertType, tdCount, currentPrice, isPerfect));
            
            boolean shouldTrigger = (alertType.equals("TD_BUY") && tdCount == 9 && isPerfect) ||
                    (alertType.equals("TD_SELL") && tdCount == 9 && isPerfect) ||
                    (alertType.equals("TD_BUY") && tdCount >= 13) ||
                    (alertType.equals("TD_SELL") && tdCount >= 13);
            
            if (shouldTrigger && !alert.getTriggered()) {
                alert.setTriggered(true);
                alert.setTriggerPrice(currentPrice);
                log.warn("TD ALERT TRIGGERED: {} {} {} count={} perfect={} price={}", symbol, interval, alertType, tdCount, isPerfect, currentPrice);
            }
            
            priceAlertRepository.save(alert);
        }
    }
    
    private String buildMessage(String symbol, String interval, String alertType, int tdCount, BigDecimal price, boolean isPerfect) {
        String direction = alertType.equals("TD_BUY") ? "买入" : "卖出";
        String perfectStr = isPerfect && tdCount == 9 ? " (完美)" : "";
        return String.format("%s %s TD%s信号%s (count=%d) 当前价格: %s", symbol, interval, direction, perfectStr, tdCount, price);
    }
    
    TDResult calculateTDCount(List<KlineBar> klines) {
        int buySetupCount = 0;
        int sellSetupCount = 0;
        boolean isPerfectBuy = false;
        boolean isPerfectSell = false;
        
        for (int i = 4; i < klines.size(); i++) {
            BigDecimal close = klines.get(i).close;
            BigDecimal closeFourAgo = klines.get(i - 4).close;
            
            if (close.compareTo(closeFourAgo) > 0) {
                buySetupCount = 0;
                sellSetupCount++;
            } else if (close.compareTo(closeFourAgo) < 0) {
                sellSetupCount = 0;
                buySetupCount++;
            } else {
                buySetupCount = 0;
                sellSetupCount = 0;
            }
            
            if (sellSetupCount == 9 && i >= 8) {
                BigDecimal high8 = klines.get(i - 1).high;
                BigDecimal high9 = klines.get(i).high;
                BigDecimal high6 = klines.get(i - 3).high;
                BigDecimal high7 = klines.get(i - 2).high;
                if (high8.compareTo(high6) > 0 && high8.compareTo(high7) > 0 ||
                    high9.compareTo(high6) > 0 && high9.compareTo(high7) > 0) {
                    isPerfectSell = true;
                }
            }
            
            if (buySetupCount == 9 && i >= 8) {
                BigDecimal low8 = klines.get(i - 1).low;
                BigDecimal low9 = klines.get(i).low;
                BigDecimal low6 = klines.get(i - 3).low;
                BigDecimal low7 = klines.get(i - 2).low;
                if (low8.compareTo(low6) < 0 && low8.compareTo(low7) < 0 ||
                    low9.compareTo(low6) < 0 && low9.compareTo(low7) < 0) {
                    isPerfectBuy = true;
                }
            }
        }
        
        return new TDResult(buySetupCount, sellSetupCount, isPerfectBuy, isPerfectSell);
    }
    
    private List<KlineBar> fetchKlines(String symbol, String interval, int limit) {
        try {
            String url = String.format("%s?symbol=%s&interval=%s&limit=%d", 
                    BINANCE_KLINE_URL, symbol, interval, limit);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseKlines(response.body());
            }
        } catch (Exception e) {
            log.error("Failed to fetch klines for {} {}: {}", symbol, interval, e.getMessage());
        }
        return null;
    }
    
    private List<KlineBar> parseKlines(String json) {
        List<KlineBar> bars = new ArrayList<>();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var array = mapper.readValue(json, List.class);
            
            for (var item : array) {
                var candle = (List<?>) item;
                bars.add(KlineBar.builder()
                        .openTime(((Number) candle.get(0)).longValue())
                        .open(new BigDecimal(candle.get(1).toString()))
                        .high(new BigDecimal(candle.get(2).toString()))
                        .low(new BigDecimal(candle.get(3).toString()))
                        .close(new BigDecimal(candle.get(4).toString()))
                        .volume(new BigDecimal(candle.get(5).toString()))
                        .closeTime(((Number) candle.get(6)).longValue())
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to parse klines: {}", e.getMessage());
        }
        return bars;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class KlineBar {
        private long openTime;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private long closeTime;
    }
    
    @lombok.Data
    private static class TDResult {
        private int buySetupCount;
        private int sellSetupCount;
        private boolean isPerfectBuy;
        private boolean isPerfectSell;
        
        TDResult(int buySetupCount, int sellSetupCount, boolean isPerfectBuy, boolean isPerfectSell) {
            this.buySetupCount = buySetupCount;
            this.sellSetupCount = sellSetupCount;
            this.isPerfectBuy = isPerfectBuy;
            this.isPerfectSell = isPerfectSell;
        }
    }
}