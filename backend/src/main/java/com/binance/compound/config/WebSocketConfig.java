package com.binance.compound.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WebSocketConfig {
    
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
