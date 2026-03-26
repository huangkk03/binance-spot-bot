package com.binance.compound.service;

import com.binance.compound.websocket.BinanceWebSocketClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {
    
    private final HttpClient httpClient;
    private final BinanceWebSocketClient webSocketClient;
    
    private final Map<String, BigDecimal> priceCache = new ConcurrentHashMap<>();
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    
    private static final String BINANCE_TICKER_URL = "https://api.binance.com/api/v3/ticker/price?symbol=%s";
    private static final String BINANCE_KLINE_URL = "https://api.binance.com/api/v3/klines?symbol=%s&interval=1m&limit=1";
    
    public BigDecimal getPrice(String symbol) {
        String normalized = symbol.toUpperCase();
        if (!normalized.endsWith("USDT")) {
            normalized = normalized + "USDT";
        }
        
        BigDecimal wsPrice = webSocketClient.getPrice(normalized);
        if (wsPrice != null && wsPrice.compareTo(BigDecimal.ZERO) > 0) {
            return wsPrice;
        }
        
        return fetchPriceFromApi(normalized);
    }
    
    public Map<String, BigDecimal> getAllPrices() {
        Map<String, BigDecimal> prices = new HashMap<>();
        
        Map<String, BigDecimal> wsPrices = webSocketClient.getAllPrices();
        prices.putAll(wsPrices);
        
        if (subscribedSymbols.isEmpty()) {
            String[] defaultSymbols = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT", "DOGEUSDT", "SOLUSDT"};
            for (String sym : defaultSymbols) {
                if (!prices.containsKey(sym) || prices.get(sym).compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal price = fetchPriceFromApi(sym);
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        prices.put(sym, price);
                    }
                }
            }
        } else {
            for (String symbol : subscribedSymbols) {
                if (!prices.containsKey(symbol) || prices.get(symbol).compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal price = fetchPriceFromApi(symbol);
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        prices.put(symbol, price);
                    }
                }
            }
        }
        
        return prices;
    }
    
    public void updatePrice(String symbol, BigDecimal price) {
        priceCache.put(symbol.toUpperCase(), price);
    }
    
    public void subscribe(String symbol) {
        String normalized = symbol.toUpperCase();
        if (!normalized.endsWith("USDT")) {
            normalized = normalized + "USDT";
        }
        subscribedSymbols.add(normalized);
        webSocketClient.subscribe(normalized);
    }
    
    public void unsubscribe(String symbol) {
        String normalized = symbol.toUpperCase();
        if (!normalized.endsWith("USDT")) {
            normalized = normalized + "USDT";
        }
        subscribedSymbols.remove(normalized);
        webSocketClient.unsubscribe(normalized);
    }
    
    private BigDecimal fetchPriceFromApi(String symbol) {
        try {
            String url = String.format(BINANCE_TICKER_URL, symbol);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                int priceIdx = body.indexOf("\"price\":\"");
                if (priceIdx > 0) {
                    int start = priceIdx + 9;
                    int end = body.indexOf("\"", start);
                    String priceStr = body.substring(start, end);
                    BigDecimal price = new BigDecimal(priceStr);
                    priceCache.put(symbol, price);
                    return price;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch price for {}: {}", symbol, e.getMessage());
        }
        
        BigDecimal cached = priceCache.get(symbol);
        return cached != null ? cached : BigDecimal.ZERO;
    }
    
    public KLineData getKLine(String symbol) {
        String normalized = symbol.toUpperCase();
        if (!normalized.endsWith("USDT")) {
            normalized = normalized + "USDT";
        }
        
        try {
            String url = String.format(BINANCE_KLINE_URL, normalized);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                return parseKLine(body);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch kline for {}: {}", symbol, e.getMessage());
        }
        
        return null;
    }
    
    private KLineData parseKLine(String json) {
        try {
            if (json.startsWith("[")) {
                ObjectMapper mapper = new ObjectMapper();
                var array = mapper.readValue(json, List.class);
                if (array != null && !array.isEmpty()) {
                    var candle = (List)array.get(0);
                    return KLineData.builder()
                            .openTime(((Number)candle.get(0)).longValue())
                            .open(new BigDecimal(candle.get(1).toString()))
                            .high(new BigDecimal(candle.get(2).toString()))
                            .low(new BigDecimal(candle.get(3).toString()))
                            .close(new BigDecimal(candle.get(4).toString()))
                            .volume(new BigDecimal(candle.get(5).toString()))
                            .closeTime(((Number)candle.get(6)).longValue())
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse kline: {}", e.getMessage());
        }
        return null;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class KLineData {
        private long openTime;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private long closeTime;
    }
}
