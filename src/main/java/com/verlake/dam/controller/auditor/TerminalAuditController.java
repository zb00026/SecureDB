package com.verlake.dam.controller.auditor;

import com.verlake.dam.entity.terminal.TerminalSessionRecording;
import com.verlake.dam.entity.terminal.TerminalCommandAudit;
import com.verlake.dam.entity.dto.TerminalSessionRecordingDTO;
import com.verlake.dam.entity.dto.TerminalCommandAuditDTO;
import com.verlake.dam.entity.dto.TerminalSessionRecordingFilter;
import com.verlake.dam.entity.dto.TerminalCommandAuditFilter;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.repository.terminal.TerminalSessionRecordingRepository;
import com.verlake.dam.repository.terminal.TerminalCommandAuditRepository;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.terminal.TerminalRecordingService;
import com.verlake.dam.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Controller for auditors to view terminal sessions and command history
 * Provides comprehensive audit trail for security and compliance
 */
@RestController
@RequestMapping("/api/audit-trails/terminal")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
public class TerminalAuditController {
    
    private final TerminalSessionRecordingRepository sessionRecordingRepository;
    private final TerminalCommandAuditRepository commandAuditRepository;
    private final UserService userService;
    private final AssetService assetService;
    private final TerminalRecordingService terminalRecordingService;
    
    /**
     * Get all terminal sessions with pagination and filtering
     */
    @GetMapping("/sessions")
    public Page<TerminalSessionRecordingDTO> getAllSessions(TerminalSessionRecordingFilter filter) {
        Specification<TerminalSessionRecording> spec = filter.toSpecification();
        
        // Order by sessionStart descending (latest to oldest), then by sessionEnd descending
        Sort sort = Sort.by(Sort.Direction.DESC, Constants.SESSION_START_FIELD)
                       .and(Sort.by(Sort.Direction.DESC, "sessionEnd"));
        Pageable pageable = filter.toPageRequest(sort);
        
        Page<TerminalSessionRecording> sessions = sessionRecordingRepository.findAll(spec, pageable);
        
        // Convert entities to DTOs and return immediately
        return new PageImpl<>(
            sessions.getContent().stream()
                .map(TerminalSessionRecordingDTO::fromEntity)
                .toList(),
            sessions.getPageable(),
            sessions.getTotalElements()
        );
    }
    
    /**
     * Get terminal session by ID
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<TerminalSessionRecording> getSession(@PathVariable String sessionId) {
        return sessionRecordingRepository.findBySessionId(sessionId)
                .map(session -> {
                    log.info("Retrieved session {} for audit by user {}", sessionId, userService.getCurrentUser().getEmail());
                    // Return cleaned session recording (without timestamp markers and ANSI sequences)
                    TerminalSessionRecording cleanedSession = terminalRecordingService.getCleanedSessionRecording(sessionId);
                    return ResponseEntity.ok(cleanedSession);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get sessions within date range
     */
    @GetMapping("/sessions/date-range")
    public ResponseEntity<Page<TerminalSessionRecording>> getSessionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // Order by sessionStart descending (latest to oldest)
        Sort sort = Sort.by(Sort.Direction.DESC, Constants.SESSION_START_FIELD);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TerminalSessionRecording> sessions = sessionRecordingRepository.findBySessionStartBetween(startDate, endDate, pageable);
        
        log.info("Retrieved {} sessions between {} and {}", sessions.getTotalElements(), startDate, endDate);
        return ResponseEntity.ok(sessions);
    }
    
    /**
     * Get active sessions
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<List<TerminalSessionRecording>> getActiveSessions() {
        // Order by sessionStart descending (latest to oldest)
        Sort sort = Sort.by(Sort.Direction.DESC, Constants.SESSION_START_FIELD);
        List<TerminalSessionRecording> activeSessions = sessionRecordingRepository.findByIsActiveTrue(sort);
        log.info("Retrieved {} active terminal sessions", activeSessions.size());
        return ResponseEntity.ok(activeSessions);
    }
    
    /**
     * Get commands for a specific session
     */
    @GetMapping("/sessions/{sessionId}/commands")
    public ResponseEntity<List<TerminalCommandAudit>> getSessionCommands(@PathVariable String sessionId) {
        // Order by executedAt descending (latest to oldest), then by commandSequence descending
        Sort sort = Sort.by(Sort.Direction.DESC, "executedAt")
                       .and(Sort.by(Sort.Direction.DESC, "commandSequence"));
        List<TerminalCommandAudit> commands = commandAuditRepository.findBySessionId(sessionId, sort);
        log.info("Retrieved {} commands for session {}", commands.size(), sessionId);
        return ResponseEntity.ok(commands);
    }
    
    /**
     * Get all commands with pagination and filtering
     */
    @GetMapping("/commands")
    public Page<TerminalCommandAuditDTO> getAllCommands(TerminalCommandAuditFilter filter) {
        Specification<TerminalCommandAudit> spec = filter.toSpecification();
        
        // Order by executedAt descending (latest to oldest), then by commandSequence descending
        Sort sort = Sort.by(Sort.Direction.DESC, "executedAt")
                       .and(Sort.by(Sort.Direction.DESC, "commandSequence"));
        Pageable pageable = filter.toPageRequest(sort);
        
        Page<TerminalCommandAudit> commands = commandAuditRepository.findAll(spec, pageable);
        
        // Convert entities to DTOs and return immediately
        return new PageImpl<>(
            commands.getContent().stream()
                .map(TerminalCommandAuditDTO::fromEntity)
                .toList(),
            commands.getPageable(),
            commands.getTotalElements()
        );
    }
    
    /**
     * Get dangerous commands
     */
    @GetMapping("/commands/dangerous")
    public ResponseEntity<Page<TerminalCommandAudit>> getDangerousCommands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<TerminalCommandAudit> dangerousCommands = commandAuditRepository.findByIsDangerousTrueOrderByExecutedAtDesc(pageable);
        
        log.warn("Retrieved {} dangerous commands for audit review", dangerousCommands.getTotalElements());
        return ResponseEntity.ok(dangerousCommands);
    }
    
    /**
     * Get commands by risk level
     */
    @GetMapping("/commands/risk/{riskLevel}")
    public ResponseEntity<Page<TerminalCommandAudit>> getCommandsByRiskLevel(
            @PathVariable String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<TerminalCommandAudit> commands = commandAuditRepository.findByRiskLevelOrderByExecutedAtDesc(riskLevel.toUpperCase(), pageable);
        
        log.info("Retrieved {} commands with risk level {} for audit", commands.getTotalElements(), riskLevel);
        return ResponseEntity.ok(commands);
    }
    
    /**
     * Search commands by content
     */
    @GetMapping("/commands/search")
    public ResponseEntity<Page<TerminalCommandAudit>> searchCommands(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        // Add wildcards for LIKE search
        String searchPattern = "%" + query + "%";
        Page<TerminalCommandAudit> commands = commandAuditRepository.searchCommands(searchPattern, pageable);
        
        log.info("Search for '{}' returned {} commands", query, commands.getTotalElements());
        return ResponseEntity.ok(commands);
    }
    
    /**
     * Get commands within date range
     */
    @GetMapping("/commands/date-range")
    public ResponseEntity<Page<TerminalCommandAudit>> getCommandsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<TerminalCommandAudit> commands = commandAuditRepository.findByExecutedAtBetween(startDate, endDate, pageable);
        
        log.info("Retrieved {} commands between {} and {}", commands.getTotalElements(), startDate, endDate);
        return ResponseEntity.ok(commands);
    }
    
    /**
     * Get recent dangerous commands (last 24 hours)
     */
    @GetMapping("/commands/recent-dangerous")
    public ResponseEntity<List<TerminalCommandAudit>> getRecentDangerousCommands() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        List<TerminalCommandAudit> recentDangerous = commandAuditRepository.findRecentDangerousCommands(cutoffTime);
        
        log.warn("Retrieved {} dangerous commands in the last 24 hours", recentDangerous.size());
        return ResponseEntity.ok(recentDangerous);
    }
    
    /**
     * Get command statistics by type
     */
    @GetMapping("/statistics/command-types")
    public ResponseEntity<Map<String, Long>> getCommandTypeStatistics() {
        List<Object[]> stats = commandAuditRepository.getCommandStatisticsByType();
        Map<String, Long> result = new HashMap<>();
        
        for (Object[] stat : stats) {
            result.put((String) stat[0], (Long) stat[1]);
        }
        
        log.info("Retrieved command type statistics: {} types", result.size());
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get risk level statistics
     */
    @GetMapping("/statistics/risk-levels")
    public ResponseEntity<Map<String, Long>> getRiskLevelStatistics() {
        List<Object[]> stats = commandAuditRepository.getRiskLevelStatistics();
        Map<String, Long> result = new HashMap<>();
        
        for (Object[] stat : stats) {
            result.put((String) stat[0], (Long) stat[1]);
        }
        
        log.info("Retrieved risk level statistics: {} levels", result.size());
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get session statistics dashboard
     */
    @GetMapping("/statistics/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStatistics() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Active sessions count
        long activeSessions = sessionRecordingRepository.findByIsActiveTrue().size();
        dashboard.put("activeSessions", activeSessions);
        
        // Total sessions today
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        long sessionsToday = sessionRecordingRepository.findBySessionStartBetween(startOfDay, endOfDay, Pageable.unpaged()).getTotalElements();
        dashboard.put("sessionsToday", sessionsToday);
        
        // Dangerous commands today
        long dangerousToday = commandAuditRepository.findByExecutedAtBetween(startOfDay, endOfDay, Pageable.unpaged())
                .getContent().stream()
                .mapToLong(cmd -> cmd.getIsDangerous() ? 1 : 0)
                .sum();
        dashboard.put("dangerousCommandsToday", dangerousToday);
        
        // Total commands today
        long commandsToday = commandAuditRepository.findByExecutedAtBetween(startOfDay, endOfDay, Pageable.unpaged()).getTotalElements();
        dashboard.put("commandsToday", commandsToday);
        
        log.info("Generated dashboard statistics for audit overview");
        return ResponseEntity.ok(dashboard);
    }
    
    /**
     * Get all assets for selection in terminal audit filtering
     * This endpoint provides a list of all available assets that can be used
     * to filter terminal audit trails by specific assets
     * 
     * @return List of AssetDTO containing all available assets
     */
    @GetMapping("/assets")
    public ResponseEntity<List<AssetDTO>> getAllAssets() {
        List<AssetDTO> assets = assetService.getAllAssets();
        return ResponseEntity.ok(assets);
    }
}
