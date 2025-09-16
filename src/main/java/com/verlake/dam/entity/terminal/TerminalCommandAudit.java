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
 * Entity for storing individual terminal commands for audit and security analysis
 * Each command executed in a terminal session is recorded separately
 */
@Entity
@Table(name = "terminal_command_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalCommandAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false)
    private String sessionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_recording_id")
    private TerminalSessionRecording sessionRecording;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "username", nullable = false)
    private String username; // SSH username used
    
    @Column(name = "command_sequence", nullable = false)
    private Integer commandSequence; // Order in session
    
    @Column(name = "raw_input", columnDefinition = "TEXT")
    private String rawInput; // Exact input received
    
    @Column(name = "parsed_command", columnDefinition = "TEXT")
    private String parsedCommand; // Cleaned/parsed command
    
    @Column(name = "command_type")
    private String commandType; // e.g., "file_operation", "system", "network", etc.
    
    @Column(name = "is_dangerous")
    private Boolean isDangerous; // Flag for potentially dangerous commands
    
    @Column(name = "risk_level")
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @Lob
    @Column(name = "command_output", columnDefinition = "LONGTEXT")
    private String commandOutput; // Response from the command
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Column(name = "client_ip")
    private String clientIp;
    
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (executedAt == null) {
            executedAt = LocalDateTime.now();
        }
        if (isDangerous == null) {
            isDangerous = false;
        }
        if (riskLevel == null) {
            riskLevel = "LOW";
        }
    }
    
    /**
     * Analyze command and set risk level and dangerous flag
     */
    public void analyzeCommand() {
        if (parsedCommand == null || parsedCommand.trim().isEmpty()) {
            return;
        }
        
        String cmd = parsedCommand.toLowerCase().trim();
        
        // Check for dangerous commands
        if (isDangerousCommand(cmd)) {
            isDangerous = true;
            riskLevel = "HIGH";
        } else if (isMediumRiskCommand(cmd)) {
            riskLevel = "MEDIUM";
        } else if (isLowRiskCommand(cmd)) {
            riskLevel = "LOW";
        }
        
        // Set command type
        commandType = determineCommandType(cmd);
    }
    
    private boolean isDangerousCommand(String cmd) {
        String[] dangerousCommands = {
            "rm -rf", "rm -r", "dd if=", "mkfs", "fdisk", "parted",
            "sudo rm", "chmod 777", "chmod -R 777", "chown -R",
            "iptables -F", "systemctl stop", "systemctl disable",
            "kill -9", "killall", "pkill", "reboot", "shutdown",
            "passwd", "useradd", "usermod", "userdel", "groupadd",
            "crontab", "at ", "nc -l", "ncat -l", "socat",
            "wget", "curl", "scp", "rsync", "tar -x"
        };
        
        for (String dangerous : dangerousCommands) {
            if (cmd.contains(dangerous)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isMediumRiskCommand(String cmd) {
        String[] mediumRiskCommands = {
            "chmod", "chown", "mount", "umount", "service",
            "systemctl", "ps aux", "netstat", "ss -", "lsof",
            "find /", "grep -r", "awk", "sed", "cut"
        };
        
        for (String medium : mediumRiskCommands) {
            if (cmd.startsWith(medium) || cmd.contains(" " + medium)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isLowRiskCommand(String cmd) {
        String[] lowRiskCommands = {
            "ls", "cd", "pwd", "cat", "less", "more", "head", "tail",
            "echo", "date", "whoami", "id", "history", "which", "whereis"
        };
        
        for (String low : lowRiskCommands) {
            if (cmd.startsWith(low)) {
                return true;
            }
        }
        return false;
    }
    
    private String determineCommandType(String cmd) {
        if (cmd.matches("^(ls|cd|pwd|mkdir|rmdir|cp|mv|rm|find|locate).*")) {
            return "file_operation";
        } else if (cmd.matches("^(ps|top|htop|kill|killall|pkill|jobs|bg|fg).*")) {
            return "process_management";
        } else if (cmd.matches("^(netstat|ss|ping|wget|curl|scp|rsync|nc|ncat).*")) {
            return "network";
        } else if (cmd.matches("^(sudo|su|passwd|useradd|usermod|userdel|chmod|chown).*")) {
            return "security";
        } else if (cmd.matches("^(systemctl|service|mount|umount|fdisk|df|du).*")) {
            return "system_admin";
        } else if (cmd.matches("^(cat|less|more|head|tail|grep|awk|sed|cut|sort|uniq).*")) {
            return "text_processing";
        } else if (cmd.matches("^(vi|vim|nano|emacs|gedit).*")) {
            return "editor";
        } else {
            return "other";
        }
    }
}
