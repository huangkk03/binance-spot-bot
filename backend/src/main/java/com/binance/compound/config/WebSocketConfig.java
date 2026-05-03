package com.binance.compound.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final com.binance.compound.websocket.FrontendWebSocketHandler frontendWebSocketHandler;

    public WebSocketConfig(com.binance.compound.websocket.FrontendWebSocketHandler frontendWebSocketHandler) {
        this.frontendWebSocketHandler = frontendWebSocketHandler;
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
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
