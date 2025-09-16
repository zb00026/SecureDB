package com.verlake.dam.entity.terminal;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing complete terminal session recordings
 * Contains all input/output data for auditing purposes
 */
@Entity
@Table(name = "terminal_session_recordings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalSessionRecording {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "username", nullable = false)
    private String username; // SSH username used
    
    @Column(name = "host_address", nullable = false)
    private String hostAddress;
    
    @Column(name = "port_number", nullable = false)
    private Integer portNumber;
    
    @Column(name = "session_start", nullable = false)
    private LocalDateTime sessionStart;
    
    @Column(name = "session_end")
    private LocalDateTime sessionEnd;
    
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    
    @Lob
    @Column(name = "full_session_log", columnDefinition = "LONGTEXT")
    private String fullSessionLog; // Complete session transcript
    
    @Column(name = "command_count")
    private Integer commandCount;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    @Column(name = "client_ip")
    private String clientIp;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "terminal_size")
    private String terminalSize; // Format: "80x24"
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (commandCount == null) {
            commandCount = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Mark session as ended and calculate duration
     */
    public void endSession() {
        if (sessionEnd == null) {
            sessionEnd = LocalDateTime.now();
            isActive = false;
            if (sessionStart != null) {
                durationSeconds = java.time.Duration.between(sessionStart, sessionEnd).getSeconds();
            }
        }
    }
}
