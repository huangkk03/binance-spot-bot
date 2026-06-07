package com.binance.compound.service;

import com.binance.compound.entity.FundingRateAlert;
import com.binance.compound.repository.FundingRateAlertRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingRateScannerService {

    private final HttpClient httpClient;
    private final FundingRateAlertRepository fundingRateAlertRepository;
    private final NotificationService notificationService;

    private static final String BINANCE_FUNDING_RATE_URL = "https://fapi.binance.com/fapi/v1/premiumIndex";

    private static final Set<String> MAIN_SYMBOLS = new HashSet<>(Arrays.asList("BTCUSDT", "ETHUSDT"));

    @Value("${funding-rate.enabled:true}")
    private boolean enabled;

    @Value("${funding-rate.symbols:BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT,DOGEUSDT}")
    private String symbolsConfig;

    @Value("${funding-rate.main-threshold-level1:-0.0005}")
    private BigDecimal mainThresholdLevel1;

    @Value("${funding-rate.main-threshold-level2:-0.001}")
    private BigDecimal mainThresholdLevel2;

    @Value("${funding-rate.alt-threshold-level1:-0.001}")
    private BigDecimal altThresholdLevel1;

    @Value("${funding-rate.alt-threshold-level2:-0.002}")
    private BigDecimal altThresholdLevel2;

    @Value("${funding-rate.cooldown-hours:2}")
    private int cooldownHours;

    @Scheduled(fixedRateString = "${funding-rate.scan-interval-ms:300000}")
    public void scanFundingRates() {
        if (!enabled) {
            log.debug("Funding rate scan is disabled");
            return;
        }

        log.info("Funding rate scan starting...");
        Set<String> symbols = new HashSet<>(Arrays.asList(symbolsConfig.split(",")));

        try {
            List<FundingRateData> allRates = fetchAllFundingRates();
            if (allRates == null || allRates.isEmpty()) {
                log.warn("No funding rate data fetched");
                return;
            }

            for (FundingRateData rate : allRates) {
                if (!symbols.contains(rate.symbol)) {
                    continue;
                }
                try {
                    processSymbol(rate);
                } catch (Exception e) {
                    log.error("Error processing funding rate for {}: {}", rate.symbol, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching funding rates: {}", e.getMessage(), e);
        }

        log.info("Funding rate scan completed.");
    }

    private List<FundingRateData> fetchAllFundingRates() {
        try {
            String url = BINANCE_FUNDING_RATE_URL;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                if (root.isArray()) {
                    return mapper.readerFor(FundingRateData.class).readValue(root);
                }
            } else {
                log.warn("Failed to fetch funding rates. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Exception fetching funding rates: {}", e.getMessage(), e);
        }
        return null;
    }

    @Transactional
    public void processSymbol(FundingRateData rateData) {
        BigDecimal fundingRate = rateData.lastFundingRate;
        if (fundingRate == null) {
            return;
        }

        BigDecimal thresholdLevel1 = MAIN_SYMBOLS.contains(rateData.symbol) ? mainThresholdLevel1 : altThresholdLevel1;
        BigDecimal thresholdLevel2 = MAIN_SYMBOLS.contains(rateData.symbol) ? mainThresholdLevel2 : altThresholdLevel2;

        log.info("Funding rate for {}: {} (thresholds: L1={}, L2={})", rateData.symbol, fundingRate, thresholdLevel1, thresholdLevel2);

        if (fundingRate.compareTo(thresholdLevel2) <= 0) {
            handleAlert(rateData, "LEVEL_2", fundingRate, thresholdLevel2);
        } else if (fundingRate.compareTo(thresholdLevel1) <= 0) {
            handleAlert(rateData, "LEVEL_1", fundingRate, thresholdLevel1);
        }
    }

    @Transactional
    public void handleAlert(FundingRateData rateData, String alertType, BigDecimal currentRate, BigDecimal threshold) {
        FundingRateAlert existing = fundingRateAlertRepository
                .findBySymbolAndAlertType(rateData.symbol, alertType).orElse(null);

        boolean shouldNotify = false;

        if (existing == null) {
            existing = FundingRateAlert.builder()
                    .symbol(rateData.symbol)
                    .alertType(alertType)
                    .fundingRate(currentRate)
                    .annualizedRate(calculateAnnualizedRate(currentRate))
                    .nextFundingTime(rateData.nextFundingTime)
                    .lastNotifiedAt(LocalDateTime.now())
                    .build();
            fundingRateAlertRepository.save(existing);
            shouldNotify = true;
            log.info("New funding rate alert created for {} {}: rate={}", rateData.symbol, alertType, currentRate);
        } else {
            LocalDateTime lastNotified = existing.getLastNotifiedAt();
            boolean inCooldown = lastNotified != null &&
                    LocalDateTime.now().isBefore(lastNotified.plusHours(cooldownHours));

            if (inCooldown) {
                log.debug("Funding rate alert in cooldown for {} {}: lastNotified={}", rateData.symbol, alertType, lastNotified);
                existing.setFundingRate(currentRate);
                existing.setAnnualizedRate(calculateAnnualizedRate(currentRate));
                existing.setNextFundingTime(rateData.nextFundingTime);
                fundingRateAlertRepository.save(existing);
            } else {
                shouldNotify = true;
                existing.setFundingRate(currentRate);
                existing.setAnnualizedRate(calculateAnnualizedRate(currentRate));
                existing.setNextFundingTime(rateData.nextFundingTime);
                existing.setLastNotifiedAt(LocalDateTime.now());
                fundingRateAlertRepository.save(existing);
                log.info("Funding rate alert triggered for {} {}: rate={}", rateData.symbol, alertType, currentRate);
            }
        }

        if (shouldNotify) {
            sendNotification(rateData, alertType, currentRate);
        }
    }

    private void sendNotification(FundingRateData rateData, String alertType, BigDecimal currentRate) {
        BigDecimal annualizedRate = calculateAnnualizedRate(currentRate);
        String countdown = calculateCountdown(rateData.nextFundingTime);

        String alertLevelText = "LEVEL_2".equals(alertType)
                ? "💥 级别二：【绝对信号】（空头极度拥挤）"
                : "🚨 级别一：【预警信号】";

        String markdown = String.format(
                "🟢 **【币安现货抄底提醒】—— 发现轧空信号！**\n" +
                "> **监控币种：** %s\n" +
                "> **信号级别：** %s\n" +
                "> **当前资金费率：** %s（年化约 %s）\n" +
                "> **下次结算时间：** 还有 %s\n" +
                "> **持仓量(OI)状态：** 已达监控阈值",
                rateData.symbol,
                alertLevelText,
                formatPercentage(currentRate),
                formatPercentage(annualizedRate),
                countdown
        );

        log.info("Sending funding rate alert notification for {}", rateData.symbol);
        notificationService.sendWeChatMarkdownNotification(markdown);
    }

    private BigDecimal calculateAnnualizedRate(BigDecimal fundingRate) {
        return fundingRate.multiply(new BigDecimal("3")).multiply(new BigDecimal("365"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String calculateCountdown(Long nextFundingTime) {
        if (nextFundingTime == null) {
            return "未知";
        }
        long now = System.currentTimeMillis();
        long diff = nextFundingTime - now;
        if (diff <= 0) {
            return "已结算";
        }
        long hours = diff / (1000 * 60 * 60);
        long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
        return String.format("%02d小时%02d分钟", hours, minutes);
    }

    private String formatPercentage(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public static class FundingRateData {
        public String symbol;
        public BigDecimal lastFundingRate;
        public Long nextFundingTime;
    }
}
