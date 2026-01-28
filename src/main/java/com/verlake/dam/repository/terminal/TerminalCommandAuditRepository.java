package com.verlake.dam.repository.terminal;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.terminal.TerminalCommandAudit;
import com.verlake.dam.entity.terminal.TerminalSessionRecording;
import com.verlake.dam.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TerminalCommandAuditRepository extends JpaRepository<TerminalCommandAudit, Long>, JpaSpecificationExecutor<TerminalCommandAudit> {
    
    /**
     * Find commands by session ID
     */
    List<TerminalCommandAudit> findBySessionIdOrderByCommandSequenceAsc(String sessionId);
    
    /**
     * Find commands by session ID with custom sorting
     */
    List<TerminalCommandAudit> findBySessionId(String sessionId, Sort sort);
    
    /**
     * Find commands by session recording
     */
    List<TerminalCommandAudit> findBySessionRecordingOrderByCommandSequenceAsc(TerminalSessionRecording sessionRecording);
    
    /**
     * Find commands by user
     */
    Page<TerminalCommandAudit> findByUserOrderByExecutedAtDesc(User user, Pageable pageable);
    
    /**
     * Find commands by asset
     */
    Page<TerminalCommandAudit> findByAssetOrderByExecutedAtDesc(Asset asset, Pageable pageable);
    
    /**
     * Find dangerous commands
     */
    Page<TerminalCommandAudit> findByIsDangerousTrueOrderByExecutedAtDesc(Pageable pageable);
    
    /**
     * Find commands by risk level
     */
    Page<TerminalCommandAudit> findByRiskLevelOrderByExecutedAtDesc(String riskLevel, Pageable pageable);
    
    /**
     * Find commands by type
     */
    Page<TerminalCommandAudit> findByCommandTypeOrderByExecutedAtDesc(String commandType, Pageable pageable);
    
    /**
     * Find commands within date range
     */
    @Query("SELECT tca FROM TerminalCommandAudit tca WHERE tca.executedAt >= :startDate AND tca.executedAt <= :endDate ORDER BY tca.executedAt DESC")
    Page<TerminalCommandAudit> findByExecutedAtBetween(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Search commands by content
     */
    @Query("SELECT tca FROM TerminalCommandAudit tca WHERE tca.parsedCommand LIKE :searchTerm OR tca.rawInput LIKE :searchTerm ORDER BY tca.executedAt DESC")
    Page<TerminalCommandAudit> searchCommands(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Find commands by user and date range
     */
    @Query("SELECT tca FROM TerminalCommandAudit tca WHERE tca.user = :user AND tca.executedAt >= :startDate AND tca.executedAt <= :endDate ORDER BY tca.executedAt DESC")
    Page<TerminalCommandAudit> findByUserAndExecutedAtBetween(
        @Param("user") User user, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Find commands by asset and date range
     */
    @Query("SELECT tca FROM TerminalCommandAudit tca WHERE tca.asset = :asset AND tca.executedAt >= :startDate AND tca.executedAt <= :endDate ORDER BY tca.executedAt DESC")
    Page<TerminalCommandAudit> findByAssetAndExecutedAtBetween(
        @Param("asset") Asset asset, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Count dangerous commands by user
     */
    @Query("SELECT COUNT(tca) FROM TerminalCommandAudit tca WHERE tca.user = :user AND tca.isDangerous = true")
    long countDangerousCommandsByUser(@Param("user") User user);
    
    /**
     * Count commands by user and date range
     */
    @Query("SELECT COUNT(tca) FROM TerminalCommandAudit tca WHERE tca.user = :user AND tca.executedAt >= :startDate AND tca.executedAt <= :endDate")
    long countCommandsByUserAndDateRange(@Param("user") User user, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get command statistics by type
     */
    @Query("SELECT tca.commandType, COUNT(tca) FROM TerminalCommandAudit tca GROUP BY tca.commandType ORDER BY COUNT(tca) DESC")
    List<Object[]> getCommandStatisticsByType();
    
    /**
     * Get risk level statistics
     */
    @Query("SELECT tca.riskLevel, COUNT(tca) FROM TerminalCommandAudit tca GROUP BY tca.riskLevel ORDER BY COUNT(tca) DESC")
    List<Object[]> getRiskLevelStatistics();
    
    /**
     * Find recent dangerous commands (last 24 hours)
     */
    @Query("SELECT tca FROM TerminalCommandAudit tca WHERE tca.isDangerous = true AND tca.executedAt >= :cutoffTime ORDER BY tca.executedAt DESC")
    List<TerminalCommandAudit> findRecentDangerousCommands(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find commands with specific patterns (for security analysis)
     * Note: Using LIKE instead of REGEXP for HQL compatibility
     */
    @Query("SELECT tca FROM TerminalCommandAudit tca WHERE tca.parsedCommand LIKE :pattern ORDER BY tca.executedAt DESC")
    List<TerminalCommandAudit> findCommandsWithPattern(@Param("pattern") String pattern);
}
