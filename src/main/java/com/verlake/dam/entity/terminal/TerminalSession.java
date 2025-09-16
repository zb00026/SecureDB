package com.verlake.dam.entity.terminal;

import com.verlake.dam.entity.assets.AssetCredential;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// SSH functionality moved to SSHConnectionService

/**
 * Entity representing a terminal session for Unix server connections
 * This class manages the lifecycle and state of a terminal session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class TerminalSession {
    
    private Long assetId;
    private String host;
    private int port; 
    private AssetCredential sshCredential;
    private long createdAt;
    @Builder.Default
    private long sessionTimeoutMs = 3600000; // 1 hour
    // Session state
    @Builder.Default
    private boolean isConnected = false;
    @Builder.Default
    private boolean isConnecting = false;
    @Builder.Default
    private StringBuilder commandHistory = new StringBuilder();
    
    // SSH connection will be managed by SSHConnectionService
    private Object sshConnection; // Will be SSHConnectionService.SSHConnection
    
    // User encryption key for SSH key decryption
    private String userKey;
    
    
    /**
     * Check if the session has expired
     * @return true if the session has exceeded its timeout
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > sessionTimeoutMs;
    }
    
    /**
     * Get the session age in milliseconds
     * @return the age of the session in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * Get the remaining time until expiration in milliseconds
     * @return the remaining time until expiration, or 0 if already expired
     */
    public long getTimeUntilExpiration() {
        long remaining = sessionTimeoutMs - getAge();
        return Math.max(0, remaining);
    }
    
    /**
     * Set SSH connection (managed by SSHConnectionService)
     * Updates connection state automatically
     */
    public void setSSHConnection(Object sshConnection) {
        this.sshConnection = sshConnection;
        this.isConnected = (sshConnection != null);
        this.isConnecting = false;
    }
    
    /**
     * Get SSH connection (managed by SSHConnectionService)
     */
    public Object getSSHConnection() {
        return sshConnection;
    }
    
    /**
     * Add input to command history
     */
    public void addToHistory(String input) {
        commandHistory.append(input);
    }
    
    /**
     * Get command history
     */
    public String getCommandHistory() {
        return commandHistory.toString();
    }
    
    /**
     * Close the terminal session
     */
    public void close() {
        log.debug("Closing terminal session for asset {} to {}:{}", assetId, host, port);
        this.isConnected = false;
        this.isConnecting = false;
        // SSH connection cleanup will be handled by SSHConnectionService
        log.info("Terminal session closed for asset {} to {}:{}", assetId, host, port);
    }
    
    /**
     * Get a unique session identifier
     * @return a unique identifier for this session
     */
    public String getSessionId() {
        return assetId + "_" + createdAt;
    }
    
    @Override
    public String toString() {
        return String.format("TerminalSession{assetId=%d, host='%s', port=%d, createdAt=%d, age=%dms}", 
                assetId, host, port, createdAt, getAge());
    }
}
