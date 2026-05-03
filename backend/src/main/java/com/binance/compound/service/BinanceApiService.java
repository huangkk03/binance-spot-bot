package com.binance.compound.service;

import com.binance.compound.entity.ApiConfig;
import com.binance.compound.repository.ApiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceApiService {
    
    private final ApiConfigRepository apiConfigRepository;
    
    private HttpClient createHttpClient(String proxyUrl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30));
        
        if (proxyUrl != null && !proxyUrl.trim().isEmpty()) {
            try {
                URI proxyUri = URI.create(proxyUrl);
                String proxyHost = proxyUri.getHost();
                int proxyPort = proxyUri.getPort();
                if (proxyHost != null && proxyPort > 0) {
                    java.net.InetSocketAddress proxyAddr = new java.net.InetSocketAddress(proxyHost, proxyPort);
                    final Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
                    ProxySelector customSelector = new ProxySelector() {
                        @Override
                        public List<Proxy> select(URI uri) {
                            return Collections.singletonList(proxy);
                        }
                        @Override
                        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                            log.warn("Proxy connection failed: {}", ioe.getMessage());
                        }
                    };
                    builder.proxy(customSelector);
                }
            } catch (Exception e) {
                log.warn("Failed to parse proxy URL: {}", proxyUrl, e);
            }
        }
        
        return builder.build();
    }
    
    public Map<String, Object> testApiConnection(String apiKey, String apiSecret, boolean testnet, String proxyUrl) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        String baseUrl = testnet ? "https://testnet.binance.vision" : "https://api.binance.com";
        
        HttpClient client = createHttpClient(proxyUrl);
        
        try {
            long timestamp = System.currentTimeMillis();
            String queryString = "timestamp=" + timestamp;
            String signature = hmacSha256(queryString, apiSecret);
            String url = baseUrl + "/api/v3/account?" + queryString + "&signature=" + signature;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-MBX-APIKEY", apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                result.put("success", true);
                result.put("message", "API连接成功");
                
                Map<String, Object> accountData = parseAccountResponse(response.body());
                result.put("account", accountData);
            } else if (response.statusCode() == -1) {
                errors.add("网络连接失败，请检查网络");
            } else {
                String errorBody = response.body();
                if (errorBody.contains("Invalid API Key")) {
                    errors.add("API Key无效");
                } else if (errorBody.contains("Signature for this request is not valid")) {
                    errors.add("API Secret无效");
                } else if (errorBody.contains("Timestamp for this request was not valid")) {
                    errors.add("请求超时，请同步时间");
                } else {
                    errors.add("API错误: " + errorBody.substring(0, Math.min(100, errorBody.length())));
                }
            }
        } catch (Exception e) {
            errors.add("连接异常: " + e.getMessage());
        }
        
        if (!errors.isEmpty()) {
            result.put("success", false);
            result.put("errors", errors);
        }
        
        return result;
    }
    
    private Map<String, Object> parseAccountResponse(String json) {
        Map<String, Object> account = new HashMap<>();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            
            List<Map<String, Object>> balances = new ArrayList<>();
            var balancesNode = node.get("balances");
            if (balancesNode != null && balancesNode.isArray()) {
                for (var balance : balancesNode) {
                    String asset = balance.get("asset").asText();
                    String free = balance.get("free").asText();
                    String locked = balance.get("locked").asText();
                    
                    BigDecimal freeAmt = new BigDecimal(free);
                    BigDecimal lockedAmt = new BigDecimal(locked);
                    
                    if (freeAmt.compareTo(BigDecimal.ZERO) > 0 || lockedAmt.compareTo(BigDecimal.ZERO) > 0) {
                        Map<String, Object> balanceMap = new HashMap<>();
                        balanceMap.put("asset", asset);
                        balanceMap.put("free", free);
                        balanceMap.put("locked", locked);
                        balanceMap.put("total", freeAmt.add(lockedAmt).toPlainString());
                        balances.add(balanceMap);
                    }
                }
            }
            
            account.put("accountType", node.has("accountType") ? node.get("accountType").asText() : "SPOT");
            account.put("balances", balances);
            account.put("updateTime", node.has("updateTime") ? node.get("updateTime").asText() : "");
            
        } catch (Exception e) {
            log.error("Failed to parse account response: {}", e.getMessage());
        }
        return account;
    }
    
    private String hmacSha256(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }
    
    public Map<String, Object> getAccountBalances(String apiKey, String apiSecret, boolean testnet, String proxyUrl) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        String baseUrl = testnet ? "https://testnet.binance.vision" : "https://api.binance.com";
        
        HttpClient client = createHttpClient(proxyUrl);
        
        try {
            long timestamp = System.currentTimeMillis();
            String queryString = "timestamp=" + timestamp;
            String signature = hmacSha256(queryString, apiSecret);
            String url = baseUrl + "/api/v3/account?" + queryString + "&signature=" + signature;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-MBX-APIKEY", apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                result.put("success", true);
                Map<String, Object> accountData = parseAccountResponse(response.body());
                result.put("account", accountData);
            } else {
                String errorBody = response.body();
                if (errorBody.contains("Invalid API Key")) {
                    errors.add("API Key无效");
                } else if (errorBody.contains("Signature for this request is not valid")) {
                    errors.add("API Secret无效");
                } else {
                    errors.add("API错误: " + errorBody.substring(0, Math.min(100, errorBody.length())));
                }
                result.put("success", false);
                result.put("errors", errors);
            }
        } catch (Exception e) {
            errors.add("连接异常: " + e.getMessage());
            result.put("success", false);
            result.put("errors", errors);
        }
        
        return result;
    }
    
    public Map<String, Object> placeMarketBuyOrder(String symbol, String quoteQuantity, String apiKey, String apiSecret, boolean testnet, String proxyUrl) {
        return placeOrder(symbol, "BUY", quoteQuantity, null, apiKey, apiSecret, testnet, proxyUrl);
    }
    
    public Map<String, Object> placeMarketSellOrder(String symbol, String quantity, String apiKey, String apiSecret, boolean testnet, String proxyUrl) {
        return placeOrder(symbol, "SELL", null, quantity, apiKey, apiSecret, testnet, proxyUrl);
    }
    
    public Map<String, Object> placeOrder(String symbol, String side, String quoteQuantity, String quantity, String apiKey, String apiSecret, boolean testnet, String proxyUrl) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        String baseUrl = testnet ? "https://testnet.binance.vision" : "https://api.binance.com";
        HttpClient client = createHttpClient(proxyUrl);
        
        try {
            long timestamp = System.currentTimeMillis();
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("symbol=").append(symbol);
            queryBuilder.append("&side=").append(side);
            queryBuilder.append("&type=MARKET");
            queryBuilder.append("&timestamp=").append(timestamp);
            
            if (quoteQuantity != null && !quoteQuantity.isEmpty()) {
                queryBuilder.append("&quoteOrderQty=").append(quoteQuantity);
            } else if (quantity != null && !quantity.isEmpty()) {
                queryBuilder.append("&quantity=").append(quantity);
            }
            
            String queryString = queryBuilder.toString();
            String signature = hmacSha256(queryString, apiSecret);
            String url = baseUrl + "/api/v3/order?" + queryString + "&signature=" + signature;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-MBX-APIKEY", apiKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                result.put("success", true);
                result.put("orderId", parseJsonField(response.body(), "orderId"));
                result.put("symbol", parseJsonField(response.body(), "symbol"));
                result.put("side", parseJsonField(response.body(), "side"));
                result.put("type", parseJsonField(response.body(), "type"));
                result.put("executedQty", parseJsonField(response.body(), "executedQty"));
                result.put("cummulativeQuoteQty", parseJsonField(response.body(), "cummulativeQuoteQty"));
                result.put("status", parseJsonField(response.body(), "status"));
                result.put("transactTime", parseJsonField(response.body(), "transactTime"));
            } else {
                String errorBody = response.body();
                if (errorBody.contains("Invalid API Key")) {
                    errors.add("API Key无效");
                } else if (errorBody.contains("Signature for this request is not valid")) {
                    errors.add("API Secret无效");
                } else if (errorBody.contains("Balance insufficient")) {
                    errors.add("余额不足");
                } else if (errorBody.contains("Filter failure")) {
                    errors.add("订单参数不符合要求: " + extractFilterFailure(errorBody));
                } else {
                    errors.add("下单失败: " + errorBody.substring(0, Math.min(100, errorBody.length())));
                }
                result.put("success", false);
                result.put("errors", errors);
            }
        } catch (Exception e) {
            errors.add("下单异常: " + e.getMessage());
            result.put("success", false);
            result.put("errors", errors);
        }
        
        return result;
    }
    
    public Map<String, Object> getSpotPrice(String symbol, boolean testnet, String proxyUrl) {
        Map<String, Object> result = new HashMap<>();
        String baseUrl = testnet ? "https://testnet.binance.vision" : "https://api.binance.com";
        HttpClient client = createHttpClient(proxyUrl);
        
        try {
            String url = baseUrl + "/api/v3/ticker/price?symbol=" + symbol;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                result.put("success", true);
                result.put("price", parseJsonField(response.body(), "price"));
                result.put("symbol", parseJsonField(response.body(), "symbol"));
            } else {
                result.put("success", false);
                result.put("error", "获取价格失败: " + response.body());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取价格异常: " + e.getMessage());
        }
        
        return result;
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        // Will be updated when first real trade or manually triggered
        // updateExchangeInfo(false, null);
    }
    
    private final Map<String, Integer> stepSizeCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> pricePrecisionCache = new ConcurrentHashMap<>();
    
    public void updateExchangeInfo(boolean testnet, String proxyUrl) {
        Map<String, Object> info = getExchangeInfo(testnet, proxyUrl);
        if (Boolean.TRUE.equals(info.get("success"))) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree((String) info.get("data"));
                var symbols = root.get("symbols");
                if (symbols != null && symbols.isArray()) {
                    for (var symNode : symbols) {
                        String symbol = symNode.get("symbol").asText();
                        var filters = symNode.get("filters");
                        if (filters != null && filters.isArray()) {
                            for (var filter : filters) {
                                String filterType = filter.get("filterType").asText();
                                if ("LOT_SIZE".equals(filterType)) {
                                    String stepSize = filter.get("stepSize").asText();
                                    int scale = Math.max(0, stepSize.indexOf('1') - stepSize.indexOf('.'));
                                    if (stepSize.indexOf('.') == -1 || stepSize.startsWith("1")) scale = 0;
                                    stepSizeCache.put(symbol, scale);
                                } else if ("PRICE_FILTER".equals(filterType)) {
                                    String tickSize = filter.get("tickSize").asText();
                                    int scale = Math.max(0, tickSize.indexOf('1') - tickSize.indexOf('.'));
                                    if (tickSize.indexOf('.') == -1 || tickSize.startsWith("1")) scale = 0;
                                    pricePrecisionCache.put(symbol, scale);
                                }
                            }
                        }
                    }
                }
                log.info("Updated exchange info cache for {} symbols", stepSizeCache.size());
            } catch (Exception e) {
                log.error("Failed to parse exchange info", e);
            }
        }
    }
    
    public int getStepSize(String symbol) {
        return stepSizeCache.getOrDefault(symbol, 8);
    }
    
    public int getPricePrecision(String symbol) {
        return pricePrecisionCache.getOrDefault(symbol, 8);
    }

    public Map<String, Object> getExchangeInfo(boolean testnet, String proxyUrl) {
        Map<String, Object> result = new HashMap<>();
        String baseUrl = testnet ? "https://testnet.binance.vision" : "https://api.binance.com";
        HttpClient client = createHttpClient(proxyUrl);
        
        try {
            String url = baseUrl + "/api/v3/exchangeInfo";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                result.put("success", true);
                result.put("data", response.body());
            } else {
                result.put("success", false);
                result.put("error", "获取交易规则失败: " + response.body());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取交易规则异常: " + e.getMessage());
        }
        
        return result;
    }
    
    private String parseJsonField(String json, String fieldName) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            if (node.has(fieldName)) {
                return node.get(fieldName).asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse field {} from json: {}", fieldName, e.getMessage());
        }
        return "";
    }
    
    private String extractFilterFailure(String json) {
        try {
            int idx = json.indexOf("Filter failure");
            if (idx > 0) {
                return json.substring(idx, Math.min(idx + 100, json.length()));
            }
        } catch (Exception e) {
        }
        return "";
    }
}
