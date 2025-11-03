package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.terminal.TerminalCommandAudit;
import com.verlake.dam.utils.SpringContext;
import com.verlake.dam.utils.TimezoneConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String executedAt; // Converted to system timezone string
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
        TimezoneConverter timezoneConverter = SpringContext.getBean(TimezoneConverter.class);
        
        return TerminalCommandAuditDTO.builder()
                .id(audit.getId())
                .sessionId(audit.getSessionId())
                .rawInput(audit.getRawInput())
                .parsedCommand(audit.getParsedCommand())
                .commandOutput(audit.getCommandOutput())
                .executionTimeMs(audit.getExecutionTimeMs())
                .clientIp(audit.getClientIp())
                .executedAt(timezoneConverter.convertToSystemTimezoneString(audit.getExecutedAt()))
                .commandSequence(audit.getCommandSequence())
                .riskLevel(audit.getRiskLevel())
                .isDangerous(audit.getIsDangerous())
                .commandType(audit.getCommandType())
                .username(audit.getUsername())
                .sessionRecording(TerminalSessionRecordingDTO.fromEntity(audit.getSessionRecording()))
                .build();
    }
}
