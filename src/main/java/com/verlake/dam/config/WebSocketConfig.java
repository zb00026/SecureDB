package com.verlake.dam.config;

import com.verlake.dam.controller.terminal.TerminalController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
    private final TerminalController terminalController;

    public WebSocketConfig(TerminalController terminalController) {
        this.terminalController = terminalController;
        logger.info("WebSocketConfig initialized with TerminalController: {}", terminalController.getClass().getSimpleName());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        logger.info("Registering WebSocket handler for /api/terminal/connect");
        registry.addHandler(terminalController, "/api/terminal/connect")
                .setAllowedOriginPatterns("*");
        logger.info("WebSocket handler registered successfully");
    }
}
