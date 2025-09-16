package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.terminal.TerminalCommandAudit;
import com.verlake.dam.entity.terminal.TerminalSessionRecording;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for TerminalCommandAudit with full entity references
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalCommandAuditDTO {
    private Long id;
    private String sessionId;
    private String rawInput;
    private String parsedCommand;
    private String commandOutput;
    private Long executionTimeMs;
    private String clientIp;
    private LocalDateTime executedAt;
    private Integer commandSequence;
    private String riskLevel;
    private Boolean isDangerous;
    private String commandType;
    private String username;
    private TerminalSessionRecordingDTO sessionRecording; // Full TerminalSessionRecording entity

    /**
     * Convert TerminalCommandAudit entity to DTO
     */
    public static TerminalCommandAuditDTO fromEntity(TerminalCommandAudit audit) {
        return TerminalCommandAuditDTO.builder()
                .id(audit.getId())
                .sessionId(audit.getSessionId())
                .rawInput(audit.getRawInput())
                .parsedCommand(audit.getParsedCommand())
                .commandOutput(audit.getCommandOutput())
                .executionTimeMs(audit.getExecutionTimeMs())
                .clientIp(audit.getClientIp())
                .executedAt(audit.getExecutedAt())
                .commandSequence(audit.getCommandSequence())
                .riskLevel(audit.getRiskLevel())
                .isDangerous(audit.getIsDangerous())
                .commandType(audit.getCommandType())
                .username(audit.getUsername())
                .sessionRecording(TerminalSessionRecordingDTO.fromEntity(audit.getSessionRecording()))
                .build();
    }
}
