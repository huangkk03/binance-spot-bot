package com.binance.compound.service;

import com.binance.compound.entity.PriceAlert;
import com.binance.compound.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RSIScannerService {

    private final HttpClient httpClient;
    private final PriceAlertRepository priceAlertRepository;
    private final AiPredictionService aiPredictionService;
    private final com.binance.compound.repository.StrategyConfigRepository strategyConfigRepository;
    private final NotificationService notificationService;

    private static final String BINANCE_KLINE_URL = "https://api.binance.com/api/v3/klines";
    
    private static final String[] SYMBOLS = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "DOGEUSDT", "SOLUSDT"};
    private static final String[] INTERVALS = {"15m", "1h", "4h", "1d"};
    
    private static final Map<String, Long> COOLDOWN_MINUTES = Map.of(
            "15m", 15L,
            "1h", 60L,
            "4h", 240L,
            "1d", 1440L
    );
    
    @Scheduled(fixedRate = 60000)
    public void scanRSIIndicators() {
        log.info("RSI scan starting...");
        for (String symbol : SYMBOLS) {
            for (String interval : INTERVALS) {
                try {
                    scanSymbolInterval(symbol, interval);
                } catch (Exception e) {
                    log.error("Error scanning RSI for {} {}: {}", symbol, interval, e.getMessage(), e);
                }
            }
        }
        log.info("RSI scan completed.");
    }

    private BigDecimal getThreshold(String symbol, String type, String defaultValue) {
        return strategyConfigRepository.findByConfigKeyAndIsSimulation("RSI_" + type + "_" + symbol, true)
                .map(c -> new BigDecimal(c.getConfigValue()))
                .orElseGet(() -> strategyConfigRepository.findByConfigKeyAndIsSimulation("RSI_" + type + "_DEFAULT", true)
                        .map(c -> new BigDecimal(c.getConfigValue()))
                        .orElse(new BigDecimal(defaultValue)));
    }

    @Transactional
    public void scanSymbolInterval(String symbol, String interval) {
        log.debug("Scanning RSI for {} {}", symbol, interval);
        List<KlineBar> klines = fetchKlines(symbol, interval, 100);
        log.debug("Fetched {} klines for {} {}", klines != null ? klines.size() : 0, symbol, interval);
        
        int rsiPeriod = getThreshold(symbol, "PERIOD", "14").intValue();
        if (klines == null || klines.size() < rsiPeriod + 1) {
            return;
        }
        
        BigDecimal rsi = calculateRSI(klines, rsiPeriod);
        if (rsi == null) return;
        log.info("RSI for {} {} = {}", symbol, interval, rsi);

        BigDecimal currentPrice = klines.get(klines.size() - 1).close;
        
        PriceAlert existingOverbought = priceAlertRepository
                .findBySymbolAndIntervalAndAlertType(symbol, interval, "RSI_OVERBOUGHT").orElse(null);
        PriceAlert existingOversold = priceAlertRepository
                .findBySymbolAndIntervalAndAlertType(symbol, interval, "RSI_OVERSOLD").orElse(null);

        BigDecimal overboughtThreshold = getThreshold(symbol, "OVERBOUGHT", "80");
        BigDecimal oversoldThreshold = getThreshold(symbol, "OVERSOLD", "20");
        log.info("RSI check for {} {}: rsi={}, overboughtThreshold={}, oversoldThreshold={}", 
                symbol, interval, rsi, overboughtThreshold, oversoldThreshold);

        if (rsi.compareTo(overboughtThreshold) >= 0) {
            handleAlert(symbol, interval, "RSI_OVERBOUGHT", rsi, currentPrice, existingOverbought, overboughtThreshold);
        } else if (existingOverbought != null) {
            existingOverbought.setTriggered(false);
            priceAlertRepository.save(existingOverbought);
        }

        if (rsi.compareTo(oversoldThreshold) <= 0) {
            handleAlert(symbol, interval, "RSI_OVERSOLD", rsi, currentPrice, existingOversold, oversoldThreshold);
        } else if (existingOversold != null) {
            existingOversold.setTriggered(false);
            priceAlertRepository.save(existingOversold);
        }
    }

    private void handleAlert(String symbol, String interval, String alertType, BigDecimal rsi,
            BigDecimal currentPrice, PriceAlert existing, BigDecimal threshold) {

        boolean isNew = existing == null;
        PriceAlert alert;
        String baseMessage = buildMessage(symbol, interval, alertType, rsi, currentPrice, threshold);
        long cooldownMinutes = COOLDOWN_MINUTES.getOrDefault(interval, 60L);

        if (isNew) {
            alert = PriceAlert.builder()
                    .symbol(symbol)
                    .interval(interval)
                    .alertType(alertType)
                    .tdCount(0)
                    .currentPrice(currentPrice)
                    .triggerPrice(currentPrice)
                    .triggered(true)
                    .lastNotifiedAt(LocalDateTime.now())
                    .message(baseMessage)
                    .build();

            appendAiAnalysisIfNeeded(alert, symbol, interval, alertType, rsi);
            priceAlertRepository.save(alert);
            notificationService.sendNotification("RSI ALERT: " + symbol + " " + interval, alert.getMessage());
            log.warn("RSI ALERT TRIGGERED: {} {} {} rsi={} price={}", symbol, interval, alertType, rsi, currentPrice);
        } else {
            alert = existing;
            alert.setCurrentPrice(currentPrice);

            // 检查冷却期
            LocalDateTime lastNotified = alert.getLastNotifiedAt();
            boolean inCooldown = lastNotified != null &&
                    LocalDateTime.now().isBefore(lastNotified.plusMinutes(cooldownMinutes));

            if (inCooldown) {
                // 在冷却期内，只更新价格，不发送通知
                priceAlertRepository.save(alert);
                log.debug("RSI ALERT in cooldown: {} {} {} cooldown={}min", symbol, interval, alertType, cooldownMinutes);
            } else if (!alert.getTriggered()) {
                alert.setTriggered(true);
                alert.setTriggerPrice(currentPrice);
                alert.setLastNotifiedAt(LocalDateTime.now());
                alert.setMessage(baseMessage);
                appendAiAnalysisIfNeeded(alert, symbol, interval, alertType, rsi);
                notificationService.sendNotification("RSI ALERT: " + symbol + " " + interval, alert.getMessage());
                log.warn("RSI ALERT TRIGGERED: {} {} {} rsi={} price={}", symbol, interval, alertType, rsi, currentPrice);
                priceAlertRepository.save(alert);
            } else {
                priceAlertRepository.save(alert);
            }
        }
    }

    private void appendAiAnalysisIfNeeded(PriceAlert alert, String symbol, String interval, String alertType, BigDecimal rsi) {
        if ("4h".equals(interval) || "1d".equals(interval)) {
            String aiAdvice = aiPredictionService.generateRsiTradingAdvice(symbol, interval, alertType, rsi);
            if (aiAdvice != null && !aiAdvice.isEmpty()) {
                alert.setMessage(alert.getMessage() + "\n\n【AI 分析建议】\n" + aiAdvice);
            }
        }
    }

    private String buildMessage(String symbol, String interval, String alertType, BigDecimal rsi, BigDecimal price, BigDecimal threshold) {
        String condition = alertType.equals("RSI_OVERBOUGHT") ? "超买(>=" + threshold + ")" : "超卖(<=" + threshold + ")";
        return String.format("%s %s RSI%s信号 (RSI=%.2f) 当前价格: %s", symbol, interval, condition, rsi, price);
    }

    BigDecimal calculateRSI(List<KlineBar> klines, int period) {
        if (klines.size() < period + 1) return null;

        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;

        for (int i = 1; i <= period; i++) {
            BigDecimal diff = klines.get(i).close.subtract(klines.get(i - 1).close);
            if (diff.compareTo(BigDecimal.ZERO) >= 0) {
                sumGain = sumGain.add(diff);
            } else {
                sumLoss = sumLoss.add(diff.abs());
            }
        }

        BigDecimal avgGain = sumGain.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLoss.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);

        for (int i = period + 1; i < klines.size(); i++) {
            BigDecimal diff = klines.get(i).close.subtract(klines.get(i - 1).close);
            BigDecimal gain = BigDecimal.ZERO;
            BigDecimal loss = BigDecimal.ZERO;

            if (diff.compareTo(BigDecimal.ZERO) >= 0) {
                gain = diff;
            } else {
                loss = diff.abs();
            }

            avgGain = (avgGain.multiply(new BigDecimal(period - 1)).add(gain)).divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
            avgLoss = (avgLoss.multiply(new BigDecimal(period - 1)).add(loss)).divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
        }

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100");
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        BigDecimal rsi = new BigDecimal("100").subtract(new BigDecimal("100").divide(rs.add(BigDecimal.ONE), 8, RoundingMode.HALF_UP));

        return rsi.setScale(2, RoundingMode.HALF_UP);
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
}
