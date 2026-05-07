package com.binance.compound.service;

import com.binance.compound.repository.ApiConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPredictionService {

    private final BinanceApiService binanceApiService;
    private final ApiConfigRepository apiConfigRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api.url:https://api.openai.com/v1/chat/completions}")
    private String defaultAiApiUrl;

    @Value("${ai.api.key:}")
    private String defaultAiApiKey;

    @Value("${ai.api.model:gpt-3.5-turbo}")
    private String defaultAiApiModel;

    private String getAiApiUrl() {
        return apiConfigRepository.findByConfigKey("AI_API_URL")
                .map(c -> c.getConfigValue())
                .filter(v -> !v.isEmpty())
                .orElse(defaultAiApiUrl);
    }

    private String getAiApiKey() {
        return apiConfigRepository.findByConfigKey("AI_API_KEY")
                .map(c -> c.getConfigValue())
                .filter(v -> !v.isEmpty())
                .orElse(defaultAiApiKey);
    }

    private String getAiApiModel() {
        return apiConfigRepository.findByConfigKey("AI_API_MODEL")
                .map(c -> c.getConfigValue())
                .filter(v -> !v.isEmpty())
                .orElse(defaultAiApiModel);
    }

    public String generateBtcPredictionReport() {
        try {
            // 1. Fetch BTC Data (Price & 24h Volume)
            Map<String, Object> tickerData = getTickerData("BTCUSDT");
            String currentPrice = (String) tickerData.getOrDefault("lastPrice", "N/A");
            String volume = (String) tickerData.getOrDefault("volume", "N/A");
            String priceChangePercent = (String) tickerData.getOrDefault("priceChangePercent", "N/A");

            // 2. Build Prompt
            String prompt = String.format(
                "请作为一名专业的加密货币分析师，生成一份关于BTC（比特币）的最新市场分析与后期走势预测报告。\n" +
                "当前市场数据：\n" +
                "- 最新价格: %s USDT\n" +
                "- 24小时交易量: %s BTC\n" +
                "- 24小时涨跌幅: %s%%\n\n" +
                "报告需包含以下部分：\n" +
                "1. 市场概况（结合当前价格和交易量）\n" +
                "2. 交易量与流动性分析\n" +
                "3. 宏观信息与情绪面分析\n" +
                "4. 后期走势预测（短期与中期）\n" +
                "5. 交易建议与风险提示\n\n" +
                "请使用专业、客观的中文进行输出，排版清晰。",
                currentPrice, volume, priceChangePercent
            );

            // 3. Call AI API
            return callAiApi(prompt);

        } catch (Exception e) {
            log.error("Failed to generate AI prediction report", e);
            return "生成报告失败: " + e.getMessage();
        }
    }

    public String generateRsiTradingAdvice(String symbol, String interval, String rsiType, java.math.BigDecimal rsiValue) {
        try {
            // 1. Fetch Data (Price & 24h Volume)
            Map<String, Object> tickerData = getTickerData(symbol);
            String currentPrice = (String) tickerData.getOrDefault("lastPrice", "N/A");
            String volume = (String) tickerData.getOrDefault("volume", "N/A");
            String priceChangePercent = (String) tickerData.getOrDefault("priceChangePercent", "N/A");

            // 2. Build Prompt
            String actionContext = "RSI_OVERBOUGHT".equals(rsiType) ? "超买 (RSI >= 80)" : "超卖 (RSI <= 20)";
            String prompt = String.format(
                "请作为一名专业的加密货币技术分析助理，针对 %s 在 %s 级别出现 %s 的极端行情，生成一份技术面的局势分析报告。\n" +
                "当前市场数据：\n" +
                "- RSI值: %s\n" +
                "- 最新价格: %s USDT\n" +
                "- 24小时交易量: %s\n" +
                "- 24小时涨跌幅: %s%%\n\n" +
                "报告必须包含以下部分：\n" +
                "1. 局势分析（结合RSI极值与当前价格）\n" +
                "2. 理论多空方向（偏多 / 偏空 / 震荡）\n" +
                "3. 关键阻力位（给出具体价格或区间）\n" +
                "4. 关键支撑位（给出具体价格或区间）\n\n" +
                "请使用专业、客观的中文进行输出，排版清晰。（声明：仅供技术交流与学习，不构成任何投资建议）",
                symbol, interval, actionContext, rsiValue.toString(), currentPrice, volume, priceChangePercent
            );

            // 3. Call AI API
            return callAiApi(prompt);

        } catch (Exception e) {
            log.error("Failed to generate RSI trading advice for {}", symbol, e);
            return "生成建议失败: " + e.getMessage();
        }
    }

    private Map<String, Object> getTickerData(String symbol) {
        Map<String, Object> result = new HashMap<>();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.binance.com/api/v3/ticker/24hr?symbol=" + symbol))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                result.put("lastPrice", node.has("lastPrice") ? node.get("lastPrice").asText() : "");
                result.put("volume", node.has("volume") ? node.get("volume").asText() : "");
                result.put("priceChangePercent", node.has("priceChangePercent") ? node.get("priceChangePercent").asText() : "");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ticker data for {}", symbol, e);
        }
        return result;
    }

    private String callAiApi(String prompt) {
        String aiApiKey = getAiApiKey();
        String aiApiUrl = getAiApiUrl();
        String aiApiModel = getAiApiModel();
        
        if (aiApiKey == null || aiApiKey.trim().isEmpty()) {
            log.warn("AI API Key is not configured. Returning mock report.");
            return getMockReport();
        }

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiApiModel);
            requestBody.put("messages", List.of(message));
            requestBody.put("temperature", 0.7);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + aiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                    JsonNode messageNode = node.get("choices").get(0).get("message");
                    if (messageNode != null && messageNode.has("content")) {
                        return messageNode.get("content").asText();
                    }
                }
                log.error("AI API returned unexpected JSON structure: {}", response.body());
                return "AI API 返回了意外的数据格式: " + response.body();
            } else {
                log.error("AI API Error: {}", response.body());
                return "AI API 调用失败，状态码: " + response.statusCode() + "，响应: " + response.body();
            }
        } catch (Exception e) {
            log.error("Exception calling AI API", e);
            return "AI API 调用异常: " + e.getMessage();
        }
    }

    public Map<String, Object> testAiConnection(String url, String key, String model) {
        Map<String, Object> result = new HashMap<>();
        if (key == null || key.trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "API Key 不能为空");
            return result;
        }
        
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", "Hello");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null && !model.isEmpty() ? model : "gpt-3.5-turbo");
            requestBody.put("messages", List.of(message));
            requestBody.put("max_tokens", 5);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String targetUrl = url != null && !url.isEmpty() ? url : "https://api.openai.com/v1/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                    result.put("success", true);
                    result.put("message", "AI 接口连接成功");
                } else {
                    result.put("success", false);
                    result.put("error", "连接成功但返回格式异常: " + response.body());
                }
            } else {
                result.put("success", false);
                result.put("error", "连接失败，状态码: " + response.statusCode() + "，响应: " + response.body());
            }
        } catch (Exception e) {
            log.error("AI connection test failed", e);
            result.put("success", false);
            result.put("error", "连接异常: " + e.getMessage());
        }
        return result;
    }

    private String getMockReport() {
        return "【系统提示】未配置 AI API Key，以下为演示报告内容：\n\n" +
               "一、 市场概况\n" +
               "当前 BTC 价格在关键支撑位附近震荡，市场整体呈现观望态势。\n\n" +
               "二、 交易量与流动性分析\n" +
               "24小时交易量显示近期主力资金活跃度有所下降，流动性集中在整数关口。\n\n" +
               "三、 宏观信息与情绪面分析\n" +
               "受近期宏观经济数据影响，市场情绪偏向谨慎，贪婪恐慌指数处于中性。\n\n" +
               "四、 后期走势预测\n" +
               "短期内预计维持区间震荡，若突破上方阻力位则有望开启新一轮上涨行情；若跌破支撑则可能进一步回调。\n\n" +
               "五、 交易建议与风险提示\n" +
               "建议控制仓位，严格设置止损，切勿盲目追涨杀跌。加密货币市场风险极高，请谨慎投资。";
    }
}