package com.verlake.dam.service.terminal;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.terminal.TerminalCommandAudit;
import com.verlake.dam.entity.terminal.TerminalSessionRecording;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.repository.terminal.TerminalCommandAuditRepository;
import com.verlake.dam.repository.terminal.TerminalSessionRecordingRepository;
import com.verlake.dam.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
// import java.util.regex.Pattern; // Removed - no longer used with keyboard event approach

/**
 * Service for recording terminal sessions and auditing commands
 * Provides comprehensive logging for security and compliance
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TerminalRecordingService {
    
    private final TerminalSessionRecordingRepository sessionRecordingRepository;
    private final TerminalCommandAuditRepository commandAuditRepository;
    
    // COMMAND_PATTERN and PROMPT_PATTERN removed - no longer used with keyboard event approach
    
    /**
     * Start recording a new terminal session
     */
    @Transactional
    public TerminalSessionRecording startSessionRecording(String sessionId, Asset asset, User user, 
                                                        String username, String hostAddress, Integer portNumber,
                                                        String clientIp, String userAgent, String terminalSize) {
        log.info("Starting session recording for session: {} user: {} asset: {}", sessionId, user.getEmail(), asset.getName());
        
        TerminalSessionRecording recording = TerminalSessionRecording.builder()
                .sessionId(sessionId)
                .asset(asset)
                .user(user)
                .username(username)
                .hostAddress(hostAddress)
                .portNumber(portNumber)
                .sessionStart(LocalDateTime.now())
                .isActive(true)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .terminalSize(terminalSize)
                .fullSessionLog("")
                .commandCount(0)
                .build();
        
        return sessionRecordingRepository.save(recording);
    }
    
    /**
     * Record terminal output and extract commands
     */
    @Transactional
    public void recordOutput(String sessionId, String output) {
        TerminalSessionRecording recording = sessionRecordingRepository.findBySessionId(sessionId)
                .orElse(null);
        
        if (recording == null) {
            log.warn(Constants.MSG_NO_RECORDING_FOUND, sessionId);
            return;
        }
        
        if (!recording.getIsActive()) {
            log.debug(Constants.MSG_RECORDING_NOT_ACTIVE, sessionId);
            return;
        }
        
        // Clean and append to full session log
        String cleanedOutput = cleanSessionLog(output);
        String currentLog = recording.getFullSessionLog();
        if (currentLog == null) {
            currentLog = "";
        }
        
        // Only append if there's actual content to avoid empty additions
        if (cleanedOutput != null && !cleanedOutput.trim().isEmpty()) {
            recording.setFullSessionLog(currentLog + cleanedOutput);
            log.debug("Added to session log for session: {} - output: [{}]", sessionId, 
                     cleanedOutput.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_ESCAPE_BACKSLASH + "n"));
        }
        
        sessionRecordingRepository.save(recording);
    }
    
    /**
     * Record user input
     */
    @Transactional
    public void recordInput(String sessionId, String input) {
        TerminalSessionRecording recording = sessionRecordingRepository.findBySessionId(sessionId)
                .orElse(null);
        
        if (recording == null) {
            log.warn(Constants.MSG_NO_RECORDING_FOUND, sessionId);
            return;
        }
        
        if (!recording.getIsActive()) {
            log.debug(Constants.MSG_RECORDING_NOT_ACTIVE, sessionId);
            return;
        }
        
        // Don't add individual keyboard input to session log - only terminal output should be recorded
        // The session log will be populated by recordOutput() method with actual terminal output
        
        // Record as command audit if it looks like a command
        if (isCommand(input)) {
            recordCommand(recording, input);
        }
        
        sessionRecordingRepository.save(recording);
    }
    
    /**
     * Record command with full audit details
     */
    @Transactional
    public void recordCommandWithDetails(String sessionId, String input, String output, 
                                        Long executionTimeMs) {
        TerminalSessionRecording recording = sessionRecordingRepository.findBySessionId(sessionId)
                .orElse(null);
        
        if (recording == null) {
            log.warn(Constants.MSG_NO_RECORDING_FOUND, sessionId);
            return;
        }
        
        if (!recording.getIsActive()) {
            log.debug(Constants.MSG_RECORDING_NOT_ACTIVE, sessionId);
            return;
        }
        
        // Don't add individual command input to session log - only terminal output should be recorded
        // The session log will be populated by recordOutput() method with actual terminal output
        
        // Record as command audit if it looks like a command
        boolean isCmd = isCommand(input);
        log.debug("Recording command for session: {} - input: [{}] - isCommand: {} - output length: {}", 
                 sessionId, input.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_ESCAPE_BACKSLASH + "n"), isCmd, output != null ? output.length() : 0);
        
        if (isCmd) {
            recordCommand(recording, input, output, executionTimeMs);
        }
        
        sessionRecordingRepository.save(recording);
    }
    
    /**
     * Update command audit record with output and completion details
     */
    @Transactional
    public void updateCommandAudit(String sessionId, String command, String output, 
                                  Long executionTimeMs) {
        try {
            // Find the most recent command audit for this session that matches the command
            List<TerminalCommandAudit> allCommands = commandAuditRepository
                    .findBySessionIdOrderByCommandSequenceAsc(sessionId);
            
            if (allCommands.isEmpty()) {
                log.warn("No command audit records found for session: {}", sessionId);
                return;
            }
            
            // Reverse to get most recent commands first
            List<TerminalCommandAudit> recentCommands = allCommands.stream()
                    .sorted((a, b) -> b.getExecutedAt().compareTo(a.getExecutedAt()))
                    .toList();
            
            // Find the most recent command that matches (within last 30 seconds)
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
            TerminalCommandAudit commandAudit = recentCommands.stream()
                    .filter(cmd -> cmd.getExecutedAt().isAfter(cutoff))
                    .filter(cmd -> cmd.getParsedCommand() != null && 
                            cmd.getParsedCommand().trim().equals(command.trim()))
                    .findFirst()
                    .orElse(null);
            
            if (commandAudit == null) {
                log.warn("No matching command audit found for command: {} in session: {} (searched {} commands)", 
                        command, sessionId, recentCommands.size());
                return;
            }
            
            log.debug("Found matching command audit for command: {} in session: {}, updating with output length: {}", 
                     command, sessionId, output != null ? output.length() : 0);
            
            // Update the command audit with completion details
            commandAudit.setCommandOutput(output);
            commandAudit.setExecutionTimeMs(executionTimeMs);
            
            commandAuditRepository.save(commandAudit);
            log.info("Successfully updated command audit for command: {} in session: {} with output", command, sessionId);
            
        } catch (Exception e) {
            log.error("Failed to update command audit for session: {} command: {}", sessionId, command, e);
        }
    }
    
    /**
     * End session recording
     */
    @Transactional
    public void endSessionRecording(String sessionId) {
        TerminalSessionRecording recording = sessionRecordingRepository.findBySessionId(sessionId)
                .orElse(null);
        
        if (recording == null) {
            log.warn(Constants.MSG_NO_RECORDING_FOUND, sessionId);
            return;
        }
        
        recording.endSession();
        sessionRecordingRepository.save(recording);
        
        log.info("Ended session recording for session: {} duration: {} seconds commands: {}", 
                sessionId, recording.getDurationSeconds(), recording.getCommandCount());
    }
    
    /**
     * Record individual command
     */
    private void recordCommand(TerminalSessionRecording recording, String rawInput) {
        recordCommand(recording, rawInput, null, null);
    }
    
    /**
     * Record individual command with full audit details
     */
    private void recordCommand(TerminalSessionRecording recording, String rawInput, String output, 
                              Long executionTimeMs) {
        try {
            // Parse and clean the command
            String parsedCommand = parseCommand(rawInput);
            
            // Create command audit record
            TerminalCommandAudit commandAudit = TerminalCommandAudit.builder()
                    .sessionId(recording.getSessionId())
                    .sessionRecording(recording)
                    .asset(recording.getAsset())
                    .user(recording.getUser())
                    .username(recording.getUsername())
                    .commandSequence(recording.getCommandCount() + 1)
                    .rawInput(rawInput)
                    .parsedCommand(parsedCommand)
                    .commandOutput(output)
                    .executionTimeMs(executionTimeMs)
                    .clientIp(recording.getClientIp())
                    .executedAt(LocalDateTime.now())
                    .build();
            
            // Analyze command for risk assessment
            commandAudit.analyzeCommand();
            
            // Save command audit
            commandAuditRepository.save(commandAudit);
            
            // Update session command count
            recording.setCommandCount(recording.getCommandCount() + 1);
            
            // Log dangerous commands
            if (commandAudit.getIsDangerous()) {
                log.warn("DANGEROUS COMMAND EXECUTED - Session: {} User: {} Command: {} Risk: {}", 
                        recording.getSessionId(), recording.getUser().getEmail(), 
                        parsedCommand, commandAudit.getRiskLevel());
            }
            
            log.debug("Recorded command for session: {} sequence: {} command: {}", 
                    recording.getSessionId(), commandAudit.getCommandSequence(), parsedCommand);
            
        } catch (Exception e) {
            log.error("Error recording command for session: {}", recording.getSessionId(), e);
        }
    }
    
    /**
     * Parse and clean command input
     */
    private String parseCommand(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        
        // Remove control characters and clean up
        String cleaned = rawInput.replaceAll("[\\x00-\\x1F\\x7F]", "");
        
        // Remove common terminal escape sequences
        cleaned = cleaned.replaceAll("\\x1B\\[[0-9;]*[mK]", "");
        
        // Trim whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
    }
    
    /**
     * Check if input looks like a command
     */
    private boolean isCommand(String input) {
        if (input == null || input.trim().isEmpty()) {
            log.trace("isCommand: false - null or empty input");
            return false;
        }
        
        String trimmed = input.trim();
        
        // Skip special keys and control characters
        if (trimmed.length() == 1 && (trimmed.charAt(0) < 32 || trimmed.charAt(0) == 127)) {
            log.trace("isCommand: false - single control character: {}", (int)trimmed.charAt(0));
            return false;
        }
        
        // Skip arrow keys and function keys
        if (trimmed.contains("\u001B[") || trimmed.contains("\u009B")) {
            log.trace("isCommand: false - contains escape sequences");
            return false;
        }
        
        // Must contain at least one alphanumeric character
        // Using safer approach to avoid ReDoS attacks
        boolean hasAlphanumeric = false;
        for (char c : trimmed.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                hasAlphanumeric = true;
                break;
            }
        }
        if (!hasAlphanumeric) {
            log.trace("isCommand: false - no alphanumeric characters");
            return false;
        }
        
        // Check if it ends with Enter/Return (check original input, not trimmed)
        // OR if it's a command being executed (from keyboard events)
        boolean hasNewline = input.endsWith("\n") || input.endsWith("\r") || input.endsWith("\r\n");
        boolean isExecutedCommand = !hasNewline && !trimmed.isEmpty(); // Commands from keyboard events don't have trailing newline
        
        log.trace("isCommand: {} - input: [{}] - hasNewline: {} - isExecutedCommand: {}", 
                 hasNewline || isExecutedCommand, input.replaceAll(Constants.REGEX_NEWLINE_PATTERN, Constants.REGEX_ESCAPE_BACKSLASH + "n"), hasNewline, isExecutedCommand);
        
        return hasNewline || isExecutedCommand;
    }
    
    // isPromptOnly method removed - no longer used with keyboard event approach
    
    /**
     * Get session recording by session ID
     */
    public TerminalSessionRecording getSessionRecording(String sessionId) {
        return sessionRecordingRepository.findBySessionId(sessionId).orElse(null);
    }
    
    /**
     * Get cleaned session recording by session ID (with cleaned full_session_log)
     */
    public TerminalSessionRecording getCleanedSessionRecording(String sessionId) {
        TerminalSessionRecording recording = sessionRecordingRepository.findBySessionId(sessionId).orElse(null);
        if (recording != null && recording.getFullSessionLog() != null) {
            // Create a copy with cleaned session log
            return TerminalSessionRecording.builder()
                    .id(recording.getId())
                    .sessionId(recording.getSessionId())
                    .asset(recording.getAsset())
                    .user(recording.getUser())
                    .username(recording.getUsername())
                    .hostAddress(recording.getHostAddress())
                    .portNumber(recording.getPortNumber())
                    .sessionStart(recording.getSessionStart())
                    .sessionEnd(recording.getSessionEnd())
                    .durationSeconds(recording.getDurationSeconds())
                    .fullSessionLog(cleanSessionLog(recording.getFullSessionLog()))
                    .commandCount(recording.getCommandCount())
                    .isActive(recording.getIsActive())
                    .clientIp(recording.getClientIp())
                    .userAgent(recording.getUserAgent())
                    .terminalSize(recording.getTerminalSize())
                    .build();
        }
        return recording;
    }
    
    /**
     * Clean session log by removing timestamp markers and ANSI escape sequences
     */
    private String cleanSessionLog(String sessionLog) {
        if (sessionLog == null || sessionLog.trim().isEmpty()) {
            return sessionLog;
        }
        
        String cleaned = sessionLog;
        
        // Remove timestamp and INPUT markers like [2025-09-11T22:22:36.652374] INPUT:
        cleaned = cleaned.replaceAll("\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+\\] INPUT: ", "");
        
        // Remove ANSI escape sequences
        cleaned = cleaned.replaceAll("\\x1b\\[[0-9;]*[a-zA-Z]", ""); // Standard ANSI escape sequences
        cleaned = cleaned.replaceAll("\\x1b\\[\\?[0-9;]*[a-zA-Z]", ""); // Extended ANSI escape sequences
        cleaned = cleaned.replaceAll("\\[\\?2004[hl]", ""); // Bracketed paste mode sequences
        
        // Clean up multiple consecutive newlines (but preserve single newlines)
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        
        // Don't trim the entire string - preserve leading/trailing whitespace that might be part of the terminal output
        // Only trim if the string is just whitespace
        if (cleaned.trim().isEmpty()) {
            cleaned = cleaned.trim();
        }
        
        log.trace("Cleaned session log - original length: {} cleaned length: {}", sessionLog.length(), cleaned.length());
        
        return cleaned;
    }
    
    /**
     * Mark all active sessions as inactive (used during server startup/restart)
     * This handles cases where the server was shut down unexpectedly and sessions
     * were left in active state
     */
    @Transactional
    public void markAllActiveSessionsAsInactive() {
        try {
            List<TerminalSessionRecording> activeSessions = sessionRecordingRepository.findByIsActiveTrue();
            
            if (activeSessions.isEmpty()) {
                log.info("No active sessions found to mark as inactive");
                return;
            }
            
            LocalDateTime now = LocalDateTime.now();
            int updatedCount = 0;
            
            for (TerminalSessionRecording session : activeSessions) {
                session.setIsActive(false);
                session.setSessionEnd(now);
                
                // Calculate duration if session start is available
                if (session.getSessionStart() != null) {
                    long durationSeconds = java.time.Duration.between(session.getSessionStart(), now).getSeconds();
                    session.setDurationSeconds(durationSeconds);
                }
                
                sessionRecordingRepository.save(session);
                updatedCount++;
                
                log.debug("Marked session as inactive: {} (User: {}, Asset: {})", 
                         session.getSessionId(), 
                         session.getUser().getEmail(),
                         session.getAsset().getName());
            }
            
            log.info("Marked {} active sessions as inactive during server startup", updatedCount);
            
        } catch (Exception e) {
            log.error("Error marking active sessions as inactive during server startup", e);
        }
    }
    
    /**
     * Update terminal size for session
     */
    @Transactional
    public void updateTerminalSize(String sessionId, int cols, int rows) {
        TerminalSessionRecording recording = sessionRecordingRepository.findBySessionId(sessionId)
                .orElse(null);
        
        if (recording != null) {
            recording.setTerminalSize(cols + "x" + rows);
            sessionRecordingRepository.save(recording);
        }
    }
    
    /**
     * Clean up old recordings (for maintenance)
     */
    @Transactional
    public void cleanupOldRecordings(LocalDateTime cutoffDate) {
        // This would typically be called by a scheduled job
        // Implementation depends on retention policy
        log.info("Cleanup of recordings older than {} would be implemented here", cutoffDate);
    }
}
