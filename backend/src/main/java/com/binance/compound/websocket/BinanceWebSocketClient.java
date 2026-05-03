package com.binance.compound.websocket;

import com.binance.compound.service.PriceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class BinanceWebSocketClient extends TextWebSocketHandler {
    
    private final WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper;
    private final PriceService priceService;
    
    public BinanceWebSocketClient(WebSocketClient webSocketClient, ObjectMapper objectMapper, @Lazy PriceService priceService) {
        this.webSocketClient = webSocketClient;
        this.objectMapper = objectMapper;
        this.priceService = priceService;
    }
    
    private WebSocketSession session;
    private final Set<String> subscribedSymbols = new CopyOnWriteArraySet<>();
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    private volatile boolean isConnecting = false;
    
    private static final String WS_URL = "wss://stream.binance.com:9443/ws";
    
    public synchronized void connect() {
        if (isConnecting || (session != null && session.isOpen())) return;
        isConnecting = true;
        try {
            log.info("Connecting to Binance WebSocket: {}", WS_URL);
            this.session = webSocketClient.doHandshake(this, WS_URL).get();
            log.info("WebSocket connected");
        } catch (Exception e) {
            log.error("Failed to connect to WebSocket: {}", e.getMessage());
            scheduleReconnect();
        } finally {
            isConnecting = false;
        }
    }
    
    private void scheduleReconnect() {
        log.info("Scheduling WebSocket reconnect in 5 seconds...");
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                connect();
            }
        }, 5000);
    }
    
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception e) {
                log.error("Error closing WebSocket: {}", e.getMessage());
            }
        }
    }
    
    public void subscribe(String symbol) {
        String streamName = symbol.toLowerCase() + "usdt@trade";
        if (subscribedSymbols.add(streamName)) {
            sendSubscription(streamName, true);
            log.info("Subscribed to {}", streamName);
        }
    }
    
    public void unsubscribe(String symbol) {
        String streamName = symbol.toLowerCase() + "usdt@trade";
        if (subscribedSymbols.remove(streamName)) {
            sendSubscription(streamName, false);
            log.info("Unsubscribed from {}", streamName);
        }
    }
    
    private void sendSubscription(String streamName, boolean subscribe) {
        if (session == null || !session.isOpen()) {
            log.warn("WebSocket not connected, cannot subscribe");
            return;
        }
        try {
            String msg = String.format(
                "{\"method\":\"%s\",\"params\":[\"%s\"],\"id\":1}",
                subscribe ? "SUBSCRIBE" : "UNSUBSCRIBE",
                streamName
            );
            session.sendMessage(new TextMessage(msg));
        } catch (Exception e) {
            log.error("Failed to send subscription: {}", e.getMessage());
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            
            if (node.has("e") && "trade".equals(node.get("e").asText())) {
                String symbol = node.get("s").asText();
                BigDecimal price = new BigDecimal(node.get("p").asText());
                
                currentPrices.put(symbol, price);
                priceService.updatePrice(symbol, price);
            }
        } catch (Exception e) {
            log.debug("Error parsing WebSocket message: {}", e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established");
        for (String symbol : subscribedSymbols) {
            sendSubscription(symbol, true);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {}", status);
        scheduleReconnect();
    }
    
    public BigDecimal getPrice(String symbol) {
        return currentPrices.get(symbol.toUpperCase());
    }
    
    public Map<String, BigDecimal> getAllPrices() {
        return new ConcurrentHashMap<>(currentPrices);
    }
}
