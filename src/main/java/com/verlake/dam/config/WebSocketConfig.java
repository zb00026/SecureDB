package com.verlake.dam.config;

import com.verlake.dam.controller.terminal.TerminalController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for both terminal and Unix group operations
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalController terminalController;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("Registering WebSocket handlers...");
        
        // Register terminal WebSocket handler (without SockJS for now)
        registry.addHandler(terminalController, "/ws/terminal/connect")
                .setAllowedOrigins("*");
        
        // Register Unix group WebSocket handler
        registry.addHandler(terminalController, "/ws/unix-groups")
                .setAllowedOrigins("*");
                
        log.info("WebSocket handlers registered successfully");
        log.info("Terminal WebSocket available at: /ws/terminal/connect");
        log.info("Unix Groups WebSocket available at: /ws/unix-groups");
    }
}
