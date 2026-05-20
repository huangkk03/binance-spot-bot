package com.binance.compound.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final com.binance.compound.websocket.FrontendWebSocketHandler frontendWebSocketHandler;

    @Value("${binance.proxy-url:}")
    private String proxyUrl;

    public WebSocketConfig(com.binance.compound.websocket.FrontendWebSocketHandler frontendWebSocketHandler) {
        this.frontendWebSocketHandler = frontendWebSocketHandler;
    }

    @PostConstruct
    public void setupProxy() {
        if (proxyUrl != null && !proxyUrl.trim().isEmpty()) {
            try {
                URI proxyUri = URI.create(proxyUrl);
                System.setProperty("http.proxyHost", proxyUri.getHost());
                System.setProperty("http.proxyPort", String.valueOf(proxyUri.getPort()));
                System.setProperty("https.proxyHost", proxyUri.getHost());
                System.setProperty("https.proxyPort", String.valueOf(proxyUri.getPort()));
                log.info("Global system proxy configured from binance.proxy-url: {}:{}", proxyUri.getHost(), proxyUri.getPort());
            } catch (Exception e) {
                log.warn("Failed to parse binance.proxy-url '{}': {}", proxyUrl, e.getMessage());
            }
        } else {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
            log.info("Proxy disabled, cleared system proxy properties");
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(frontendWebSocketHandler, "/ws/frontend").setAllowedOrigins("*");
    }
    
    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
    }
    
    @Bean
    public HttpClient httpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
                
        if (proxyUrl != null && !proxyUrl.trim().isEmpty()) {
            try {
                URI proxyUri = URI.create(proxyUrl);
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
            } catch (Exception e) {
                // Already logged in setupProxy
            }
        }
        
        return builder.build();
    }
}
