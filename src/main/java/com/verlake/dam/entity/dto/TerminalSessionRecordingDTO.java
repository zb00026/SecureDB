package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.terminal.TerminalSessionRecording;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for TerminalSessionRecording with full entity references
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalSessionRecordingDTO {
    private Long id;
    private String sessionId;
    private LocalDateTime sessionStart;
    private LocalDateTime sessionEnd;
    private Long durationSeconds;
    private Integer commandCount;
    private Boolean isActive;
    private String clientIp;
    private String userAgent;
    private String terminalSize;
    private String fullSessionLog;
    private User user; // Full User entity
    private AssetDTO asset; // Full Asset DTO

    /**
     * Convert TerminalSessionRecording entity to DTO
     */
    public static TerminalSessionRecordingDTO fromEntity(TerminalSessionRecording recording) {
        return TerminalSessionRecordingDTO.builder()
                .id(recording.getId())
                .sessionId(recording.getSessionId())
                .sessionStart(recording.getSessionStart())
                .sessionEnd(recording.getSessionEnd())
                .durationSeconds(recording.getDurationSeconds())
                .commandCount(recording.getCommandCount())
                .isActive(recording.getIsActive())
                .clientIp(recording.getClientIp())
                .userAgent(recording.getUserAgent())
                .terminalSize(recording.getTerminalSize())
                .fullSessionLog(cleanSessionLog(recording.getFullSessionLog()))
                .user(recording.getUser())
                .asset(AssetDTO.fromEntity(recording.getAsset()))
                .build();
    }
    
    /**
     * Clean session log by removing timestamp markers and ANSI escape sequences
     */
    private static String cleanSessionLog(String sessionLog) {
        if (sessionLog == null || sessionLog.trim().isEmpty()) {
            return sessionLog;
        }
        
        // Remove timestamp and INPUT markers like [2025-09-11T22:22:36.652374] INPUT:
        String cleaned = sessionLog.replaceAll("\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+\\] INPUT: ", "");
        
        // Remove ANSI escape sequences
        cleaned = cleaned.replaceAll("\\x1b\\[[0-9;]*[a-zA-Z]", ""); // Standard ANSI escape sequences
        cleaned = cleaned.replaceAll("\\x1b\\[\\?[0-9;]*[a-zA-Z]", ""); // Extended ANSI escape sequences
        cleaned = cleaned.replaceAll("\\[\\?2004[hl]", ""); // Bracketed paste mode sequences
        
        // Clean up multiple consecutive newlines
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        
        // Trim whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
    }
}
