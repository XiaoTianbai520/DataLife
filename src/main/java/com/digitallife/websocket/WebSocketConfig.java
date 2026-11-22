package com.digitallife.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.stream.Stream;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer, WebMvcConfigurer {

    private final EventPushService eventPushService;
    private final String[] allowedOrigins;

    public WebSocketConfig(EventPushService eventPushService,
                           @Value("${digitallife.web.allowed-origins:}") String[] allowedOrigins) {
        this.eventPushService = eventPushService;
        this.allowedOrigins = Stream.concat(Stream.of("http://tauri.localhost"), Stream.of(allowedOrigins))
                .distinct().toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventPushService, "/ws/events")
                .setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**").allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH");
    }
}
