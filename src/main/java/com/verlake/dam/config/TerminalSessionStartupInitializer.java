package com.verlake.dam.config;

import com.verlake.dam.service.terminal.TerminalRecordingService;
import com.verlake.dam.service.terminal.TerminalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Component to handle terminal session cleanup during server startup and shutdown
 * Marks all active sessions as inactive when the server starts/restarts or shuts down
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TerminalSessionStartupInitializer {
    
    private final TerminalRecordingService terminalRecordingService;
    private final TerminalService terminalService;
    
    /**
     * Handle application startup - mark all active sessions as inactive
     * This runs after the application context is fully loaded
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Run early in the startup process
    public void handleApplicationReady() {
        log.info("=== Terminal Session Startup Initializer ===");
        log.info("Marking all active terminal sessions as inactive...");
        
        try {
            // Clear any active sessions from memory (in case of server restart)
            terminalService.clearAllActiveSessions();
            
            // Mark all active sessions in database as inactive
            terminalRecordingService.markAllActiveSessionsAsInactive();
            
            log.info("Terminal session cleanup completed successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup terminal sessions during startup", e);
        }
        
        log.info("=== Terminal Session Startup Initializer Complete ===");
    }
    
    /**
     * Handle application shutdown - mark all active sessions as inactive
     * This runs when the application is shutting down gracefully
     */
    @EventListener(ContextClosedEvent.class)
    @Order(1) // Run early in the shutdown process
    public void handleApplicationStopping() {
        log.info("=== Terminal Session Shutdown Handler ===");
        log.info("Marking all active terminal sessions as inactive during shutdown...");
        
        try {
            // Mark all active sessions in database as inactive
            terminalRecordingService.markAllActiveSessionsAsInactive();
            
            // Clear active sessions from memory
            terminalService.clearAllActiveSessions();
            
            log.info("Terminal session cleanup completed successfully during shutdown");
        } catch (Exception e) {
            log.error("Failed to cleanup terminal sessions during shutdown", e);
        }
        
        log.info("=== Terminal Session Shutdown Handler Complete ===");
    }
}
